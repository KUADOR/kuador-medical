package com.kuador.medical.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * Clinic onboarding and administration by the SUPER_ADMIN (MEDK-66; MEDK-49 CA 4 tenant and
 * subscription part, D4, D8): mandatory data named by field, RUC of 13 digits unique among
 * non-purged clinics, TRIAL or ACTIVE start depending on the plan, audit without personal data in
 * the logs, and isolation between clinics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PlatformTenantsIT {

    private static final String TENANTS = "/api/v1/platform/tenants";
    private static final String TRIAL_PLAN_ID = "6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d";
    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");

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
    void onboardingWithTheTrialPlanCreatesTheClinicInTrialAndIsAudited() {
        Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);

        MvcTestResult created = createTenant(request, null);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> tenant = api.body(created);
        contract.assertResponse("/platform/tenants", "POST", 201, "application/json", tenant);
        String id = tenant.get("id").toString();
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/platform/tenants/" + id);
        assertThat(tenant).containsEntry("legalName", request.get("legalName"))
                .containsEntry("tradeName", request.get("tradeName"))
                .containsEntry("ruc", request.get("ruc"))
                .containsEntry("clinicType", "MEDICAL")
                .containsEntry("countryCode", "EC")
                .containsEntry("mspEstablishmentNumber", null)
                .containsEntry("version", 0);
        Map<String, Object> owner = cast(tenant.get("owner"));
        assertThat(owner).containsEntry("fullName", "Ana Prueba").containsEntry("twoFactorEnabled", false);
        assertThat(owner.get("maskedEmail").toString()).startsWith("a***@").doesNotContain(ownerLocalPart(request));

        Map<String, Object> subscription = cast(tenant.get("subscription"));
        int trialDays = setting("TRIAL_DAYS");
        assertThat(subscription).containsEntry("status", "TRIAL").containsEntry("planId", TRIAL_PLAN_ID)
                .containsEntry("billingPeriod", "MONTHLY").containsEntry("currentPeriodEnd", null);
        assertThat(Instant.parse(subscription.get("trialEndsAt").toString()))
                .isEqualTo(LocalDate.now(GUAYAQUIL).plusDays(trialDays).atStartOfDay(GUAYAQUIL).toInstant());

        assertThat(jdbc.queryForObject("select tenant_id from subscription where tenant_id = ?::uuid", UUID.class, id))
                .as("the subscription belongs to the new clinic").hasToString(id);
        assertThat(jdbc.queryForObject("select price_amount_snapshot is null from subscription where tenant_id = ?::uuid",
                Boolean.class, id)).as("the trial has no price").isTrue();
        assertThat(jdbc.queryForObject("select count(*) from identity_role where tenant_id = ?::uuid", Integer.class, id))
                .as("TenantCreated seeds the clinic roles in the same transaction").isPositive();
        assertThat(audited("TENANT_ONBOARDED", id)).isOne();
    }

    @Test
    void onboardingWithAPaidPlanStartsActiveWithAPeriodFromTodayAndThePriceSnapshot() {
        String planId = createPaidPlan();

        MvcTestResult created = createTenant(tenantRequest(planId, "ANNUAL"), null);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> tenant = api.body(created);
        Map<String, Object> subscription = cast(tenant.get("subscription"));
        Instant start = LocalDate.now(GUAYAQUIL).atStartOfDay(GUAYAQUIL).toInstant();
        Instant end = LocalDate.now(GUAYAQUIL).plusYears(1).atStartOfDay(GUAYAQUIL).toInstant();
        assertThat(subscription).containsEntry("status", "ACTIVE").containsEntry("billingPeriod", "ANNUAL")
                .containsEntry("trialEndsAt", null).containsEntry("planName", "Estándar");
        assertThat(Instant.parse(subscription.get("currentPeriodEnd").toString())).isEqualTo(end);

        String id = tenant.get("id").toString();
        Map<String, Object> stored = jdbc.queryForMap("""
                select price_amount_snapshot::text as amount, price_currency_snapshot as currency, current_period_start
                from subscription where tenant_id = ?::uuid""", id);
        assertThat(stored).containsEntry("amount", "490.00").containsEntry("currency", "USD");
        assertThat(((java.sql.Timestamp) stored.get("current_period_start")).toInstant()).isEqualTo(start);
        assertThat(jdbc.queryForMap("""
                select amount::text as amount, period_start, period_end from subscription_period where tenant_id = ?::uuid""", id))
                .as("the contracted price of the period is kept (ADR-006)")
                .containsEntry("amount", "490.00");
    }

    @Test
    void everyMissingMandatoryFieldIsNamed() {
        Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);
        for (String field : List.of("legalName", "ruc", "clinicType", "countryCode", "city", "address", "phone",
                "contactEmail", "planId", "billingPeriod")) {
            request.remove(field);
        }
        request.put("owner", new LinkedHashMap<>(Map.of("fullName", " ")));
        int before = count("tenant");

        MvcTestResult result = createTenant(request, null);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = api.body(result);
        contract.assertResponse("/platform/tenants", "POST", 400, "application/problem+json", body);
        assertThat(body).containsEntry("code", "VALIDATION");
        assertThat(errors(body)).contains(
                Map.of("field", "legalName", "code", "REQUIRED"), Map.of("field", "ruc", "code", "REQUIRED"),
                Map.of("field", "clinicType", "code", "REQUIRED"), Map.of("field", "countryCode", "code", "REQUIRED"),
                Map.of("field", "city", "code", "REQUIRED"), Map.of("field", "address", "code", "REQUIRED"),
                Map.of("field", "phone", "code", "REQUIRED"), Map.of("field", "contactEmail", "code", "REQUIRED"),
                Map.of("field", "planId", "code", "REQUIRED"), Map.of("field", "billingPeriod", "code", "REQUIRED"),
                Map.of("field", "owner.fullName", "code", "REQUIRED"), Map.of("field", "owner.email", "code", "REQUIRED"));
        assertThat(count("tenant")).as("nothing is created").isEqualTo(before);
    }

    @Test
    void rucMustHaveThirteenDigits() {
        for (String ruc : List.of("123456789012", "12345678901234", "17900000000AB")) {
            Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);
            request.put("ruc", ruc);
            MvcTestResult result = createTenant(request, null);
            assertThat(result).as(ruc).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(errors(api.body(result))).as(ruc).contains(Map.of("field", "ruc", "code", "INVALID_FORMAT"));
        }
    }

    @Test
    void duplicateRucIsRejectedWhileTheClinicIsNotPurged() {
        Map<String, Object> first = tenantRequest(TRIAL_PLAN_ID);
        String firstId = api.body(createTenant(first, null)).get("id").toString();
        Map<String, Object> second = tenantRequest(TRIAL_PLAN_ID);
        second.put("ruc", first.get("ruc"));
        int before = count("tenant");

        MvcTestResult duplicate = createTenant(second, null);

        assertCode(duplicate, HttpStatus.CONFLICT, "RUC_ALREADY_REGISTERED");
        contract.assertResponse("/platform/tenants", "POST", 409, "application/problem+json", api.body(duplicate));
        assertThat(count("tenant")).isEqualTo(before);

        jdbc.update("update tenant set purged_at = now() where id = ?::uuid", firstId);
        assertThat(createTenant(second, null)).as("a purged clinic frees its RUC (D8)").hasStatus(HttpStatus.CREATED);
    }

    @Test
    void unknownOrArchivedPlanAndPaidPlanWithoutPriceAreRejected() {
        assertCode(createTenant(tenantRequest(UUID.randomUUID().toString()), null), HttpStatus.NOT_FOUND, "NOT_FOUND");

        String archived = createPaidPlan();
        assertThat(api.post("/api/v1/platform/plans/" + archived + "/archive", admin.token(), Map.of())).hasStatus(HttpStatus.OK);
        assertCode(createTenant(tenantRequest(archived), null), HttpStatus.CONFLICT, "PLAN_ARCHIVED");

        Map<String, Object> plan = planRequest();
        plan.put("prices", List.of(Map.of("billingPeriod", "MONTHLY", "currency", "USD", "amount", "49.00")));
        String monthlyOnly = api.body(api.post("/api/v1/platform/plans", admin.token(), plan)).get("id").toString();
        MvcTestResult noPrice = createTenant(tenantRequest(monthlyOnly, "ANNUAL"), null);
        assertThat(noPrice).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(errors(api.body(noPrice))).contains(Map.of("field", "billingPeriod", "code", "PRICE_NOT_AVAILABLE"));
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameClinic() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);

        MvcTestResult first = createTenant(request, key);
        MvcTestResult retry = createTenant(request, key);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        String id = api.body(first).get("id").toString();
        assertThat(api.body(retry).get("id")).isEqualTo(id);
        assertThat(audited("TENANT_ONBOARDED", id)).isOne();
        assertCode(createTenant(tenantRequest(TRIAL_PLAN_ID), key), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void clientGeneratedIdIsUsedOnceOnly() {
        String id = UUID.randomUUID().toString();
        Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);
        request.put("id", id);
        assertThat(api.body(createTenant(request, null))).containsEntry("id", id);

        Map<String, Object> again = tenantRequest(TRIAL_PLAN_ID);
        again.put("id", id);
        assertCode(createTenant(again, null), HttpStatus.CONFLICT, "RESOURCE_ID_TAKEN");
    }

    @Test
    void getReturnsTheClinicAndUnknownOrPurgedClinicsAreNotFound() {
        String id = createdTenantId();

        MvcTestResult found = api.get(TENANTS + "/" + id, admin.token());

        assertThat(found).hasStatus(HttpStatus.OK);
        contract.assertResponse("/platform/tenants/{tenantId}", "GET", 200, "application/json", api.body(found));
        assertThat(api.body(found)).containsEntry("id", id);
        assertCode(api.get(TENANTS + "/" + UUID.randomUUID(), admin.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");

        jdbc.update("update tenant set purged_at = now() where id = ?::uuid", id);
        assertCode(api.get(TENANTS + "/" + id, admin.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertCode(api.patch(TENANTS + "/" + id, admin.token(), Map.of("city", "Cuenca", "version", 0)),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void listSearchesByNameOrRucAndFiltersByStatus() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> trial = tenantRequest(TRIAL_PLAN_ID);
        trial.put("legalName", "Clínica Buscada " + marker + " S.A.");
        String trialId = api.body(createTenant(trial, null)).get("id").toString();
        Map<String, Object> paid = tenantRequest(createPaidPlan());
        paid.put("tradeName", "Dental " + marker.toUpperCase());
        String paidId = api.body(createTenant(paid, null)).get("id").toString();
        String purgedId = createdTenantId();
        jdbc.update("update tenant set purged_at = now() where id = ?::uuid", purgedId);

        MvcTestResult byName = api.get(TENANTS + "?q=" + marker, admin.token());
        assertThat(byName).hasStatus(HttpStatus.OK);
        Map<String, Object> page = api.body(byName);
        contract.assertResponse("/platform/tenants", "GET", 200, "application/json", page);
        assertThat(items(page)).extracting(item -> item.get("id")).containsExactlyInAnyOrder(trialId, paidId);
        assertThat(page).containsEntry("totalItems", 2).containsEntry("page", 0).containsEntry("size", 20);
        assertThat(items(page)).filteredOn(item -> item.get("id").equals(paidId)).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("planName", "Estándar")
                        .containsEntry("subscriptionStatus", "ACTIVE"));

        assertThat(items(api.body(api.get(TENANTS + "?q=" + marker + "&status=TRIAL", admin.token()))))
                .extracting(item -> item.get("id")).containsExactly(trialId);
        assertThat(items(api.body(api.get(TENANTS + "?q=" + paid.get("ruc"), admin.token()))))
                .extracting(item -> item.get("id")).containsExactly(paidId);
        assertThat(items(api.body(api.get(TENANTS + "?size=100", admin.token()))))
                .extracting(item -> item.get("id")).as("purged clinics are excluded").doesNotContain(purgedId);
        Map<String, Object> second = api.body(api.get(TENANTS + "?q=" + marker + "&size=1&page=1", admin.token()));
        assertThat(items(second)).hasSize(1);
        assertThat(second).containsEntry("totalItems", 2);

        MvcTestResult badSize = api.get(TENANTS + "?size=101", admin.token());
        assertThat(badSize).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.get(TENANTS + "?status=UNKNOWN", admin.token())).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchChangesOnlyTheSentFieldsWithOptimisticLockingAndIsAudited() {
        Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);
        request.put("mspEstablishmentNumber", "MSP-0001");
        String id = api.body(createTenant(request, null)).get("id").toString();
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("phone", "+593000000001");
        change.put("mspEstablishmentNumber", null);
        change.put("version", 0);

        MvcTestResult updated = api.patch(TENANTS + "/" + id, admin.token(), change);

        assertThat(updated).hasStatus(HttpStatus.OK);
        Map<String, Object> tenant = api.body(updated);
        contract.assertResponse("/platform/tenants/{tenantId}", "PATCH", 200, "application/json", tenant);
        assertThat(tenant).containsEntry("phone", "+593000000001").containsEntry("mspEstablishmentNumber", null)
                .containsEntry("legalName", request.get("legalName")).containsEntry("city", "Quito")
                .containsEntry("version", 1);
        assertThat(audited("TENANT_UPDATED", id)).isOne();

        assertCode(api.patch(TENANTS + "/" + id, admin.token(), change), HttpStatus.CONFLICT, "VERSION_CONFLICT");
        assertThat(audited("TENANT_UPDATED", id)).isOne();
    }

    @Test
    void patchRejectsClearingMandatoryFieldsUnknownFieldsAndATakenRuc() {
        Map<String, Object> other = tenantRequest(TRIAL_PLAN_ID);
        createTenant(other, null);
        String id = createdTenantId();
        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("legalName", null);
        clear.put("city", " ");
        clear.put("ruc", "12");
        clear.put("planId", TRIAL_PLAN_ID);
        clear.put("version", 0);

        MvcTestResult invalid = api.patch(TENANTS + "/" + id, admin.token(), clear);

        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        contract.assertResponse("/platform/tenants/{tenantId}", "PATCH", 400, "application/problem+json", api.body(invalid));
        assertThat(errors(api.body(invalid))).contains(
                Map.of("field", "legalName", "code", "REQUIRED"), Map.of("field", "city", "code", "REQUIRED"),
                Map.of("field", "ruc", "code", "INVALID_FORMAT"), Map.of("field", "planId", "code", "UNKNOWN_FIELD"));

        MvcTestResult taken = api.patch(TENANTS + "/" + id, admin.token(), Map.of("ruc", other.get("ruc"), "version", 0));
        assertCode(taken, HttpStatus.CONFLICT, "RUC_ALREADY_REGISTERED");
        contract.assertResponse("/platform/tenants/{tenantId}", "PATCH", 409, "application/problem+json", api.body(taken));
        assertThat(audited("TENANT_UPDATED", id)).isZero();
    }

    @Test
    void clinicUsersCannotListReadOrChangeAnyClinicIncludingTheirOwn() {
        Clinic clinicA = api.clinic("Clinica A Plataforma");
        String clinicB = createdTenantId();
        String legalNameBefore = jdbc.queryForObject("select legal_name from tenant where id = ?::uuid", String.class, clinicB);
        int before = count("tenant");

        List<MvcTestResult> attempts = List.of(
                api.get(TENANTS, clinicA.token()),
                api.post(TENANTS, clinicA.token(), tenantRequest(TRIAL_PLAN_ID)),
                api.get(TENANTS + "/" + clinicB, clinicA.token()),
                api.patch(TENANTS + "/" + clinicB, clinicA.token(), Map.of("legalName", "Tomada", "version", 0)),
                api.get(TENANTS + "/" + clinicA.tenantId(), clinicA.token()),
                api.patch(TENANTS + "/" + clinicA.tenantId(), clinicA.token(), Map.of("city", "Loja", "version", 0)));

        for (MvcTestResult attempt : attempts) {
            assertCode(attempt, HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        assertThat(count("tenant")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select legal_name from tenant where id = ?::uuid", String.class, clinicB))
                .isEqualTo(legalNameBefore);
    }

    @Test
    void subscriptionsAreIsolatedPerClinic() {
        String clinicA = createdTenantId();
        String clinicB = api.body(createTenant(tenantRequest(createPaidPlan()), null)).get("id").toString();

        assertThat(cast(api.body(api.get(TENANTS + "/" + clinicA, admin.token())).get("subscription")))
                .containsEntry("status", "TRIAL");
        assertThat(cast(api.body(api.get(TENANTS + "/" + clinicB, admin.token())).get("subscription")))
                .containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForObject(
                "select count(*) from subscription where tenant_id in (?::uuid, ?::uuid)", Integer.class, clinicA, clinicB))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from subscription_period p join subscription s on s.id = p.subscription_id
                where p.tenant_id <> s.tenant_id""", Integer.class))
                .as("a period never points to the subscription of another clinic").isZero();
    }

    @Test
    void noRucEmailNameOrPhoneReachesTheLogs(CapturedOutput output) {
        Map<String, Object> request = tenantRequest(TRIAL_PLAN_ID);
        String id = api.body(createTenant(request, null)).get("id").toString();
        createTenant(request, null);
        Map<String, Object> invalid = tenantRequest(TRIAL_PLAN_ID);
        invalid.put("ruc", "12");
        createTenant(invalid, null);
        api.patch(TENANTS + "/" + id, admin.token(), Map.of("phone", "+593999999999", "version", 0));
        api.get(TENANTS + "?q=" + request.get("ruc"), admin.token());

        String logs = output.getAll();
        for (String personal : List.of(request.get("ruc").toString(), request.get("contactEmail").toString(),
                cast(request.get("owner")).get("email").toString(), request.get("legalName").toString(),
                "Ana Prueba", "+593999999999")) {
            assertThat(logs).doesNotContain(personal);
        }
        assertThat(jdbc.queryForList("select target_type from audit_event where tenant_id = ?::uuid", String.class, id))
                .as("the audit keeps identifiers only").containsOnly("TENANT");
    }

    // ------------------------------------------------------------------ helpers

    private String createdTenantId() {
        MvcTestResult created = createTenant(tenantRequest(TRIAL_PLAN_ID), null);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private String createPaidPlan() {
        MvcTestResult created = api.post("/api/v1/platform/plans", admin.token(), planRequest());
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private static Map<String, Object> planRequest() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("code", "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        plan.put("name", "Estándar");
        plan.put("trial", false);
        plan.put("modules", List.of("AGENDA", "PATIENTS", "USERS"));
        plan.put("limits", Map.of("MAX_USERS", 15));
        plan.put("prices", List.of(
                Map.of("billingPeriod", "MONTHLY", "currency", "USD", "amount", "49.00"),
                Map.of("billingPeriod", "ANNUAL", "currency", "USD", "amount", "490.00")));
        return plan;
    }

    private MvcTestResult createTenant(Map<String, Object> request, String idempotencyKey) {
        var call = mvc.post().uri(TENANTS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request));
        if (idempotencyKey != null) {
            call = call.header("Idempotency-Key", idempotencyKey);
        }
        return call.exchange();
    }

    static Map<String, Object> tenantRequest(String planId) {
        return tenantRequest(planId, "MONTHLY");
    }

    static Map<String, Object> tenantRequest(String planId, String billingPeriod) {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("fullName", "Ana Prueba");
        owner.put("email", "ana." + marker + "@example.test");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("legalName", "Clínica Ejemplo " + marker + " S.A.");
        request.put("tradeName", "Clínica Ejemplo " + marker);
        request.put("ruc", randomRuc());
        request.put("clinicType", "MEDICAL");
        request.put("countryCode", "EC");
        request.put("city", "Quito");
        request.put("address", "Avenida Ejemplo 123");
        request.put("phone", "+593000000000");
        request.put("contactEmail", "contacto." + marker + "@example.test");
        request.put("mspEstablishmentNumber", null);
        request.put("planId", planId);
        request.put("billingPeriod", billingPeriod);
        request.put("owner", owner);
        return request;
    }

    static String randomRuc() {
        return String.format("%013d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 10_000_000_000_000L));
    }

    private static String ownerLocalPart(Map<String, Object> request) {
        String email = cast(request.get("owner")).get("email").toString();
        return email.substring(0, email.indexOf('@'));
    }

    private int setting(String key) {
        return jdbc.queryForObject("select value from platform_setting where key = ?", Integer.class, key);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private int audited(String action, String tenantId) {
        return jdbc.queryForObject("""
                select count(*) from audit_event
                where action = ? and target_type = 'TENANT' and target_id = ?::uuid and tenant_id = ?::uuid
                  and actor_account_id = ?""",
                Integer.class, action, tenantId, tenantId, admin.accountId());
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result)
                .hasStatus(status)
                .bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }

    private static List<Map<String, Object>> errors(Map<String, Object> body) {
        return castList(body.get("errors"));
    }

    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return castList(page.get("items"));
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
