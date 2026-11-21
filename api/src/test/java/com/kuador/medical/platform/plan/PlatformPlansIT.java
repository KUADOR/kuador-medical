package com.kuador.medical.platform.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plans, versioned prices and catalogs of the SUPER_ADMIN (MEDK-65, MEDK-49 CA 1, D9): every
 * change is audited as a platform event, plans are archived instead of deleted and prices are
 * immutable versions. Permissions are covered by {@code PlatformPermissionsIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformPlansIT {

    private static final String PLANS = "/api/v1/platform/plans";
    private static final String TRIAL_PLAN_ID = "6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ContractSchema contract = ContractSchema.platform();
    private IdentityApi api;
    private Admin admin;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
    }

    @Test
    void superAdminCreatesAPlanWithModulesLimitsAndMonthlyAndAnnualPricesInUsd() {
        String code = uniqueCode();

        MvcTestResult created = createPlan(planRequest(code), null);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> plan = api.body(created);
        contract.assertResponse("/platform/plans", "POST", 201, "application/json", plan);
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/platform/plans/" + plan.get("id"));
        assertThat(plan).containsEntry("code", code).containsEntry("trial", false).containsEntry("status", "ACTIVE")
                .containsEntry("version", 0);
        assertThat(plan.get("modules")).isEqualTo(List.of("AGENDA", "PATIENTS", "USERS"));
        Map<String, Object> limits = cast(plan.get("limits"));
        assertThat(limits).containsOnlyKeys("MAX_BRANCHES", "MAX_USERS", "MAX_PATIENTS", "MAX_APPOINTMENTS", "MAX_STORAGE_MB")
                .containsEntry("MAX_BRANCHES", 3).containsEntry("MAX_USERS", 15).containsEntry("MAX_APPOINTMENTS", null);
        List<Map<String, Object>> prices = castList(plan.get("currentPrices"));
        assertThat(prices).extracting(price -> price.get("billingPeriod")).containsExactly("MONTHLY", "ANNUAL");
        assertThat(prices).extracting(price -> price.get("amount")).containsExactly("49.00", "490.00");
        assertThat(prices).allSatisfy(price -> assertThat(price).containsEntry("currency", "USD"));

        assertThat(api.get(PLANS + "/" + plan.get("id"), admin.token())).hasStatus(HttpStatus.OK);
        assertThat(audited("PLAN_CREATED", "PLAN", plan.get("id"))).isOne();
    }

    @Test
    void listIncludesTheSeededTrialPlanAndFiltersByStatus() {
        MvcTestResult result = api.get(PLANS, admin.token());

        assertThat(result).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> plans = api.bodyList(result);
        contract.assertArrayResponse("/platform/plans", "GET", 200, "application/json", plans);
        Map<String, Object> trial = plans.stream().filter(plan -> plan.get("id").equals(TRIAL_PLAN_ID)).findFirst().orElseThrow();
        assertThat(trial).containsEntry("trial", true).containsEntry("currentPrices", List.of());
        assertThat(trial.get("modules"))
                .isEqualTo(List.of("AGENDA", "PATIENTS", "CLINICAL_RECORDS", "CONSULTATION", "ODONTOGRAM", "USERS"));
        assertThat(cast(trial.get("limits"))).containsEntry("MAX_BRANCHES", 1).containsEntry("MAX_USERS", 3)
                .containsEntry("MAX_PATIENTS", 50).containsEntry("MAX_APPOINTMENTS", 100).containsEntry("MAX_STORAGE_MB", 500);

        String archivedId = createdPlanId();
        assertThat(api.post(PLANS + "/" + archivedId + "/archive", admin.token(), Map.of())).hasStatus(HttpStatus.OK);
        assertThat(api.bodyList(api.get(PLANS + "?status=ACTIVE", admin.token())))
                .extracting(plan -> plan.get("id")).contains(TRIAL_PLAN_ID).doesNotContain(archivedId);
        assertThat(api.bodyList(api.get(PLANS + "?status=ARCHIVED", admin.token())))
                .extracting(plan -> plan.get("id")).contains(archivedId).doesNotContain(TRIAL_PLAN_ID);
    }

    @Test
    void duplicateCodeAndSecondTrialPlanAreConflicts() {
        String code = uniqueCode();
        assertThat(createPlan(planRequest(code), null)).hasStatus(HttpStatus.CREATED);

        assertFieldError(createPlan(planRequest(code), null), HttpStatus.CONFLICT, "code", "DUPLICATE");

        Map<String, Object> trial = planRequest(uniqueCode());
        trial.put("trial", true);
        trial.put("prices", List.of());
        assertFieldError(createPlan(trial, null), HttpStatus.CONFLICT, "trial", "DUPLICATE");
    }

    @Test
    void invalidPlansAreRejectedWithTheirField() {
        Map<String, Object> unknownModule = planRequest(uniqueCode());
        unknownModule.put("modules", List.of("AGENDA", "TELEPORTATION"));
        assertFieldError(createPlan(unknownModule, null), HttpStatus.BAD_REQUEST, "modules[1]", "UNKNOWN_MODULE");

        Map<String, Object> unknownLimit = planRequest(uniqueCode());
        unknownLimit.put("limits", Map.of("MAX_ROCKETS", 1));
        assertFieldError(createPlan(unknownLimit, null), HttpStatus.BAD_REQUEST, "limits.MAX_ROCKETS", "UNKNOWN_LIMIT");

        Map<String, Object> repeatedPeriod = planRequest(uniqueCode());
        repeatedPeriod.put("prices", List.of(price("MONTHLY", "49.00"), price("MONTHLY", "50.00")));
        assertFieldError(createPlan(repeatedPeriod, null), HttpStatus.BAD_REQUEST, "prices[1].billingPeriod", "DUPLICATE");

        Map<String, Object> otherCurrency = planRequest(uniqueCode());
        otherCurrency.put("prices", List.of(Map.of("billingPeriod", "MONTHLY", "currency", "EUR", "amount", "49.00")));
        assertFieldError(createPlan(otherCurrency, null), HttpStatus.BAD_REQUEST, "prices[0].currency", "UNSUPPORTED_CURRENCY");

        Map<String, Object> badAmount = planRequest(uniqueCode());
        badAmount.put("prices", List.of(price("MONTHLY", "49.5")));
        assertFieldError(createPlan(badAmount, null), HttpStatus.BAD_REQUEST, "prices[0].amount", "INVALID_FORMAT");

        Map<String, Object> withoutName = planRequest(uniqueCode());
        withoutName.remove("name");
        MvcTestResult missingName = createPlan(withoutName, null);
        assertFieldError(missingName, HttpStatus.BAD_REQUEST, "name", "REQUIRED");
        contract.assertResponse("/platform/plans", "POST", 400, "application/problem+json", api.body(missingName));
    }

    @Test
    void sameIdempotencyKeyReturnsTheSamePlanAndADifferentBodyIsAConflict() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = planRequest(uniqueCode());

        MvcTestResult first = createPlan(request, key);
        MvcTestResult retry = createPlan(request, key);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(api.body(retry).get("id")).isEqualTo(api.body(first).get("id"));
        assertThat(audited("PLAN_CREATED", "PLAN", api.body(first).get("id"))).isOne();
        assertThat(createPlan(planRequest(uniqueCode()), key))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void updateReplacesModulesAndLimitsWithOptimisticLockingAndIsAudited() {
        String id = createdPlanId();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", "Estándar plus");
        update.put("description", null);
        update.put("modules", List.of("AGENDA", "NOTIFICATIONS"));
        update.put("limits", new HashMap<>(Map.of("MAX_USERS", 20)));
        update.put("version", 0);

        MvcTestResult updated = api.put(PLANS + "/" + id, admin.token(), update);

        assertThat(updated).hasStatus(HttpStatus.OK);
        Map<String, Object> plan = api.body(updated);
        contract.assertResponse("/platform/plans/{planId}", "PUT", 200, "application/json", plan);
        assertThat(plan).containsEntry("name", "Estándar plus").containsEntry("version", 1);
        assertThat(plan.get("modules")).isEqualTo(List.of("AGENDA", "NOTIFICATIONS"));
        assertThat(cast(plan.get("limits"))).containsEntry("MAX_USERS", 20).containsEntry("MAX_BRANCHES", null);
        assertThat(castList(plan.get("currentPrices"))).as("prices are not changed by an update").hasSize(2);
        assertThat(audited("PLAN_UPDATED", "PLAN", id)).isOne();

        assertThat(api.put(PLANS + "/" + id, admin.token(), update))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VERSION_CONFLICT"));
        assertThat(audited("PLAN_UPDATED", "PLAN", id)).isOne();
    }

    @Test
    void archivingKeepsThePlanAndItsPricesAndBlocksChanges() {
        String id = createdPlanId();

        MvcTestResult archived = api.post(PLANS + "/" + id + "/archive", admin.token(), Map.of());

        assertThat(archived).hasStatus(HttpStatus.OK);
        contract.assertResponse("/platform/plans/{planId}/archive", "POST", 200, "application/json", api.body(archived));
        assertThat(api.body(archived)).containsEntry("status", "ARCHIVED");
        assertThat(jdbc.queryForObject("select count(*) from plan where id = ?::uuid", Integer.class, id)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from plan_price where plan_id = ?::uuid", Integer.class, id)).isEqualTo(2);
        assertThat(audited("PLAN_ARCHIVED", "PLAN", id)).isOne();

        assertThat(api.post(PLANS + "/" + id + "/archive", admin.token(), Map.of())).as("archiving again is a no-op")
                .hasStatus(HttpStatus.OK);
        assertThat(audited("PLAN_ARCHIVED", "PLAN", id)).isOne();

        Map<String, Object> update = Map.of("name", "X", "modules", List.of(), "limits", Map.of(), "version", 1);
        assertCode(api.put(PLANS + "/" + id, admin.token(), update), HttpStatus.CONFLICT, "PLAN_ARCHIVED");
        assertCode(createPrice(id, priceVersion("MONTHLY", "60.00", inOneMonth()), null), HttpStatus.CONFLICT, "PLAN_ARCHIVED");
        assertThat(mvc.delete().uri(PLANS + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .as("plans are never deleted")
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void trialPlanCannotBeArchivedNorPriced() {
        assertFieldError(api.post(PLANS + "/" + TRIAL_PLAN_ID + "/archive", admin.token(), Map.of()),
                HttpStatus.BAD_REQUEST, "trial", "TRIAL_PLAN_REQUIRED");
        assertFieldError(createPrice(TRIAL_PLAN_ID, priceVersion("MONTHLY", "10.00", inOneMonth()), null),
                HttpStatus.BAD_REQUEST, "billingPeriod", "TRIAL_PLAN_HAS_NO_PRICES");
        assertThat(jdbc.queryForObject("select status from plan where id = ?::uuid", String.class, TRIAL_PLAN_ID))
                .isEqualTo("ACTIVE");
    }

    @Test
    void newPriceIsAnImmutableVersionAndKeepsTheHistory() {
        String id = createdPlanId();
        Instant effectiveFrom = inOneMonth();

        MvcTestResult created = createPrice(id, priceVersion("MONTHLY", "55.00", effectiveFrom), UUID.randomUUID().toString());

        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> price = api.body(created);
        contract.assertResponse("/platform/plans/{planId}/prices", "POST", 201, "application/json", price);
        assertThat(price).containsEntry("billingPeriod", "MONTHLY").containsEntry("currency", "USD")
                .containsEntry("amount", "55.00");
        assertThat(Instant.parse(price.get("effectiveFrom").toString())).isEqualTo(effectiveFrom);
        assertThat(audited("PLAN_PRICE_CREATED", "PLAN_PRICE", price.get("id"))).isOne();

        List<Map<String, Object>> history = api.bodyList(api.get(PLANS + "/" + id + "/prices?billingPeriod=MONTHLY", admin.token()));
        contract.assertArrayResponse("/platform/plans/{planId}/prices", "GET", 200, "application/json", history);
        assertThat(history).extracting(version -> version.get("amount")).containsExactly("55.00", "49.00");
        assertThat(api.bodyList(api.get(PLANS + "/" + id + "/prices", admin.token()))).hasSize(3);

        Map<String, Object> plan = api.body(api.get(PLANS + "/" + id, admin.token()));
        assertThat(castList(plan.get("currentPrices")))
                .as("the new price is not in force before effectiveFrom")
                .extracting(current -> current.get("amount")).containsExactly("49.00", "490.00");
    }

    @Test
    void priceInThePastIsRejected() {
        String id = createdPlanId();

        assertFieldError(createPrice(id, priceVersion("ANNUAL", "500.00", Instant.now().minus(1, ChronoUnit.DAYS)), null),
                HttpStatus.BAD_REQUEST, "effectiveFrom", "IN_THE_PAST");
        assertThat(api.bodyList(api.get(PLANS + "/" + id + "/prices", admin.token()))).hasSize(2);
    }

    @Test
    void unknownPlanIsNotFound() {
        String unknown = UUID.randomUUID().toString();

        assertCode(api.get(PLANS + "/" + unknown, admin.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertCode(api.get(PLANS + "/" + unknown + "/prices", admin.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertCode(api.post(PLANS + "/" + unknown + "/archive", admin.token(), Map.of()), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void catalogsListEveryModuleAndLimitWithASpanishLabel() {
        List<Map<String, Object>> modules = api.bodyList(api.get("/api/v1/platform/catalog/modules", admin.token()));
        List<Map<String, Object>> limits = api.bodyList(api.get("/api/v1/platform/catalog/limits", admin.token()));

        contract.assertArrayResponse("/platform/catalog/modules", "GET", 200, "application/json", modules);
        contract.assertArrayResponse("/platform/catalog/limits", "GET", 200, "application/json", limits);
        assertThat(modules).extracting(module -> module.get("code")).containsExactly(
                "AGENDA", "PATIENTS", "CLINICAL_RECORDS", "CONSULTATION", "ODONTOGRAM", "USERS", "PHARMACY", "WAREHOUSE",
                "CASH_REGISTER", "COLLECTIONS", "NOTIFICATIONS", "E_INVOICING", "PUBLIC_API", "ADVANCED_REPORTS");
        assertThat(modules).allSatisfy(module -> assertThat(module.get("label")).asString().isNotBlank());
        assertThat(limits).extracting(limit -> limit.get("code")).containsExactly(
                "MAX_BRANCHES", "MAX_USERS", "MAX_PATIENTS", "MAX_APPOINTMENTS", "MAX_STORAGE_MB");
        assertThat(limits).filteredOn(limit -> limit.get("code").equals("MAX_STORAGE_MB"))
                .singleElement().satisfies(limit -> assertThat(limit).containsEntry("unit", "MEGABYTES"));
    }

    private String createdPlanId() {
        MvcTestResult created = createPlan(planRequest(uniqueCode()), null);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private MvcTestResult createPlan(Map<String, Object> request, String idempotencyKey) {
        return post(PLANS, request, idempotencyKey);
    }

    private MvcTestResult createPrice(String planId, Map<String, Object> request, String idempotencyKey) {
        return post(PLANS + "/" + planId + "/prices", request, idempotencyKey);
    }

    private MvcTestResult post(String uri, Map<String, Object> body, String idempotencyKey) {
        var request = mvc.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return request.exchange();
    }

    private int audited(String action, String targetType, Object targetId) {
        return jdbc.queryForObject("""
                select count(*) from audit_event
                where action = ? and target_type = ? and target_id = ?::uuid and actor_account_id = ? and tenant_id is null""",
                Integer.class, action, targetType, targetId.toString(), admin.accountId());
    }

    static String uniqueCode() {
        return "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    static Map<String, Object> planRequest(String code) {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("MAX_BRANCHES", 3);
        limits.put("MAX_USERS", 15);
        limits.put("MAX_PATIENTS", 5000);
        limits.put("MAX_APPOINTMENTS", null);
        limits.put("MAX_STORAGE_MB", 10240);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("code", code);
        request.put("name", "Estándar");
        request.put("description", "Clínica médica con hasta tres sedes.");
        request.put("trial", false);
        request.put("modules", List.of("USERS", "AGENDA", "PATIENTS"));
        request.put("limits", limits);
        request.put("prices", List.of(price("MONTHLY", "49.00"), price("ANNUAL", "490.00")));
        return request;
    }

    private static Map<String, Object> price(String billingPeriod, String amount) {
        return Map.of("billingPeriod", billingPeriod, "currency", "USD", "amount", amount);
    }

    private static Map<String, Object> priceVersion(String billingPeriod, String amount, Instant effectiveFrom) {
        return Map.of("billingPeriod", billingPeriod, "amount", amount, "effectiveFrom", effectiveFrom.toString());
    }

    private static Instant inOneMonth() {
        return Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private void assertFieldError(MvcTestResult result, HttpStatus status, String field, String code) {
        assertThat(result).hasStatus(status);
        Map<String, Object> body = api.body(result);
        assertThat(body).containsEntry("code", "VALIDATION");
        assertThat(castList(body.get("errors"))).contains(Map.of("field", field, "code", code));
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result)
                .hasStatus(status)
                .bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
