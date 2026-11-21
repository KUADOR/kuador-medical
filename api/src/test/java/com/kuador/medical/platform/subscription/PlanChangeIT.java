package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.PlanLimits;
import com.kuador.medical.identity.StaffFixture;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import com.kuador.medical.platform.api.PlanCapabilities;
import com.kuador.medical.platform.api.PlanChangeApplied;
import com.kuador.medical.platform.api.PlanChangeKind;
import com.kuador.medical.platform.api.PlanChangeScheduled;
import com.kuador.medical.platform.api.PlanUpgraded;
import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plan changes (MEDK-69; MEDK-49 CA 8 and 9, plan D2 and D3): an upgrade applies at once with the
 * proration of the remaining days and publishes {@link PlanUpgraded}; a downgrade previews the
 * usage above the target limits, is scheduled for the renewal and, when it applies, keeps every
 * record while new ones of that kind are rejected ({@code PLAN_LIMIT_REACHED}). Also the rejected
 * requests, idempotency, audit and that only a SUPER_ADMIN changes the plan of a clinic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class PlanChangeIT {

    private static final String TENANTS = "/api/v1/platform/tenants";
    private static final String TRIAL_PLAN_ID = "6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d";
    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");
    private static final String SUBSCRIPTION = "/platform/tenants/{tenantId}/subscription";
    private static final String PREVIEW = SUBSCRIPTION + "/plan-change-preview";
    private static final String CHANGES = SUBSCRIPTION + "/plan-changes";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubscriptionLifecycleJob job;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private PlanCapabilities capabilities;

    @Autowired
    @Qualifier("subscriptionCapabilities")
    private PlanLimits planLimits;

    private final ContractSchema contract = ContractSchema.platform();
    private IdentityApi api;
    private Admin admin;
    private StaffFixture staff;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
        staff = new StaffFixture(jdbc, passwordEncoder);
    }

    // ------------------------------------------------------------------ upgrade (CA 8)

    @Test
    void anUpgradeAppliesAtOnceWithTheProrationOfTheRemainingDays() {
        String standard = createPlan("Estándar", "49.00", List.of("AGENDA", "PATIENTS", "USERS"), Map.of("MAX_USERS", 15));
        String premium = createPlan("Premium", "99.00", List.of("AGENDA", "PATIENTS", "USERS", "PHARMACY"), Map.of());
        UUID clinic = onboard(standard);
        LocalDate today = LocalDate.now(GUAYAQUIL);
        setPeriod(clinic, today.minusDays(10), today.plusDays(20));

        Map<String, Object> preview = preview(clinic, premium, "MONTHLY");

        // (99 − 49) × 20 remaining days ÷ 30 days of the period
        assertThat(preview).containsEntry("kind", "UPGRADE").containsEntry("proratedAmount", "33.33")
                .containsEntry("newPriceAmount", "99.00").containsEntry("currency", "USD")
                .containsEntry("excess", List.of()).containsEntry("disabledModules", List.of());
        assertThat(getSubscription(clinic)).as("a preview changes nothing").containsEntry("plan",
                getSubscription(clinic).get("plan")).satisfies(s -> assertThat(cast(s.get("plan"))).containsEntry("id", standard));

        Instant before = Instant.now();
        MvcTestResult applied = requestChange(clinic, premium, "MONTHLY", version(getSubscription(clinic)), null);

        assertThat(applied).hasStatus(HttpStatus.CREATED);
        Map<String, Object> change = api.body(applied);
        contract.assertResponse(CHANGES, "POST", 201, "application/json", change);
        assertThat(change).containsEntry("kind", "UPGRADE").containsEntry("status", "APPLIED")
                .containsEntry("proratedAmount", "33.33").containsEntry("currency", "USD");
        assertThat(Instant.parse(change.get("effectiveAt").toString())).isBetween(before, Instant.now());
        Map<String, Object> subscription = getSubscription(clinic);
        assertThat(cast(subscription.get("plan"))).containsEntry("id", premium);
        assertThat(subscription).containsEntry("priceAmount", "99.00").containsEntry("status", "ACTIVE")
                .containsEntry("scheduledPlanChange", null);
        assertThat(events.stream(PlanUpgraded.class).filter(e -> e.tenantId().equals(clinic)))
                .singleElement().satisfies(event -> {
                    assertThat(event.proratedAmount()).isEqualByComparingTo("33.33");
                    assertThat(event.fromPlanId()).hasToString(standard);
                    assertThat(event.toPlanId()).hasToString(premium);
                    assertThat(event.currency()).isEqualTo("USD");
                });
        assertThat(audited(clinic, "PLAN_CHANGE_APPLIED", admin.accountId())).isOne();
        assertThat(jdbc.queryForObject("select prorated_amount from plan_change where tenant_id = ?", BigDecimal.class,
                clinic)).isEqualByComparingTo("33.33");
        assertThat(capabilities.isModuleEnabled(clinic, com.kuador.medical.platform.plan.ModuleCode.PHARMACY)).isTrue();
    }

    @Test
    void aTrialChangesPlanAtOnceWithoutProrationAndStaysInTrial() {
        String standard = createPlan("Estándar", "49.00", List.of("AGENDA", "PATIENTS", "USERS"), Map.of());
        UUID clinic = onboard(TRIAL_PLAN_ID);

        Map<String, Object> change = api.body(requestChange(clinic, standard, "MONTHLY", null, null));

        assertThat(change).containsEntry("status", "APPLIED").containsEntry("proratedAmount", null);
        Map<String, Object> subscription = getSubscription(clinic);
        assertThat(subscription).containsEntry("status", "TRIAL").containsEntry("priceAmount", null);
        assertThat(cast(subscription.get("plan"))).containsEntry("id", standard);
        assertThat(events.stream(PlanChangeApplied.class).filter(e -> e.tenantId().equals(clinic))).hasSize(1);
        assertThat(events.stream(PlanUpgraded.class).filter(e -> e.tenantId().equals(clinic))).isEmpty();
    }

    // ------------------------------------------------------------------ downgrade (CA 9)

    @Test
    void aDowngradeShowsTheExcessIsScheduledAndKeepsEveryRecordWhenItApplies() {
        String standard = createPlan("Estándar", "49.00", List.of("AGENDA", "PATIENTS", "USERS"), Map.of("MAX_USERS", 15));
        String basic = createPlan("Básico", "29.00", List.of("AGENDA", "USERS"), Map.of("MAX_USERS", 1));
        UUID clinic = onboard(standard);
        staff.staffUser(clinic);
        staff.staffUser(clinic);
        long seats = seatedStaff(clinic);
        assertThat(seats).isGreaterThan(1);
        Instant periodEnd = Instant.parse(getSubscription(clinic).get("currentPeriodEnd").toString());

        Map<String, Object> preview = preview(clinic, basic, "MONTHLY");

        assertThat(preview).containsEntry("kind", "DOWNGRADE").containsEntry("proratedAmount", null)
                .containsEntry("newPriceAmount", "29.00").containsEntry("disabledModules", List.of("PATIENTS"));
        assertThat(maps(preview.get("excess"))).singleElement().satisfies(excess -> assertThat(excess)
                .containsEntry("limit", "MAX_USERS").containsEntry("limitValue", 1).containsEntry("usage", (int) seats));
        assertThat(Instant.parse(preview.get("effectiveAt").toString())).isEqualTo(periodEnd);

        Map<String, Object> scheduled = api.body(requestChange(clinic, basic, "MONTHLY", null, null));

        assertThat(scheduled).containsEntry("kind", "DOWNGRADE").containsEntry("status", "SCHEDULED");
        Map<String, Object> subscription = getSubscription(clinic);
        assertThat(cast(subscription.get("plan"))).as("until the renewal").containsEntry("id", standard);
        assertThat(cast(subscription.get("scheduledPlanChange"))).containsEntry("id", scheduled.get("id"))
                .containsEntry("status", "SCHEDULED");
        assertThat(events.stream(PlanChangeScheduled.class).filter(e -> e.tenantId().equals(clinic)))
                .singleElement().satisfies(e -> assertThat(e.effectiveAt()).isEqualTo(periodEnd));
        assertThat(audited(clinic, "PLAN_CHANGE_SCHEDULED", admin.accountId())).isOne();

        // The renewal date passes without payment: the job applies the downgrade and the clinic is past due.
        Instant renewal = startOfDay(LocalDate.now(GUAYAQUIL).minusDays(1));
        jdbc.update("update subscription set current_period_end = ? where tenant_id = ?", Timestamp.from(renewal), clinic);
        jdbc.update("update plan_change set effective_at = ? where tenant_id = ?", Timestamp.from(renewal), clinic);
        job.runOnce(Instant.now());

        Map<String, Object> renewed = getSubscription(clinic);
        assertThat(renewed).containsEntry("status", "PAST_DUE").containsEntry("priceAmount", "29.00")
                .containsEntry("scheduledPlanChange", null);
        assertThat(cast(renewed.get("plan"))).containsEntry("id", basic);
        assertThat(seatedStaff(clinic)).as("nothing is deleted for exceeding a limit").isEqualTo(seats);
        assertThat(events.stream(PlanChangeApplied.class).filter(e -> e.tenantId().equals(clinic)))
                .singleElement().satisfies(e -> assertThat(e.kind()).isEqualTo(PlanChangeKind.DOWNGRADE));
        assertThat(audited(clinic, "PLAN_CHANGE_APPLIED", null)).isOne();
        assertThat(planLimits.userLimit(clinic)).hasValue(1);
        assertThatThrownBy(() -> capabilities.assertCanCreate(clinic, LimitCode.MAX_USERS))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PLAN_LIMIT_REACHED));
        assertThat(capabilities.isModuleEnabled(clinic, com.kuador.medical.platform.plan.ModuleCode.PATIENTS)).isFalse();

        // The payment of the overdue period charges the new price.
        publisher.publishEvent(new PaymentReceived(clinic, "pay-" + UUID.randomUUID(), new BigDecimal("29.00"), "USD",
                Instant.now()));
        Map<String, Object> active = getSubscription(clinic);
        assertThat(active).containsEntry("status", "ACTIVE").containsEntry("priceAmount", "29.00");
        assertThat(maps(active.get("periods"))).filteredOn(period -> renewal.toString().equals(period.get("periodStart")))
                .singleElement().satisfies(period -> assertThat(period).containsEntry("amount", "29.00")
                        .containsEntry("planName", "Básico"));
    }

    @Test
    void aRenewalPaidBeforeTheEndOfThePeriodOpensTheNewPeriodWithTheScheduledPlan() {
        String standard = createPlan("Estándar", "49.00", List.of("AGENDA", "PATIENTS", "USERS"), Map.of());
        String annualPrices = createPlan("Estándar anual", "45.00", List.of("AGENDA", "PATIENTS", "USERS"), Map.of());
        UUID clinic = onboard(standard);
        Map<String, Object> before = getSubscription(clinic);

        Map<String, Object> change = api.body(requestChange(clinic, annualPrices, "ANNUAL", null, null));
        assertThat(change).containsEntry("kind", "PERIOD_CHANGE").containsEntry("status", "SCHEDULED");

        publisher.publishEvent(new PaymentReceived(clinic, "pay-" + UUID.randomUUID(), new BigDecimal("450.00"), "USD",
                Instant.now()));

        Map<String, Object> renewed = getSubscription(clinic);
        Instant start = Instant.parse(renewed.get("currentPeriodStart").toString());
        assertThat(start).isEqualTo(Instant.parse(before.get("currentPeriodEnd").toString()));
        assertThat(Instant.parse(renewed.get("currentPeriodEnd").toString()))
                .isEqualTo(LocalDate.ofInstant(start, GUAYAQUIL).plusYears(1).atStartOfDay(GUAYAQUIL).toInstant());
        assertThat(renewed).containsEntry("billingPeriod", "ANNUAL").containsEntry("priceAmount", "450.00")
                .containsEntry("scheduledPlanChange", null);
        assertThat(cast(renewed.get("plan"))).containsEntry("id", annualPrices);
    }

    @Test
    void aNewRequestReplacesTheScheduledChange() {
        String standard = createPlan("Estándar", "49.00", List.of("USERS"), Map.of());
        String basic = createPlan("Básico", "29.00", List.of("USERS"), Map.of());
        String lite = createPlan("Lite", "19.00", List.of("USERS"), Map.of());
        UUID clinic = onboard(standard);

        Map<String, Object> first = api.body(requestChange(clinic, basic, "MONTHLY", null, null));
        Map<String, Object> second = api.body(requestChange(clinic, lite, "MONTHLY", null, null));

        assertThat(cast(getSubscription(clinic).get("scheduledPlanChange"))).containsEntry("id", second.get("id"));
        assertThat(jdbc.queryForObject("select status from plan_change where id = ?", String.class,
                UUID.fromString(first.get("id").toString()))).isEqualTo("CANCELLED");
        assertThat(audited(clinic, "PLAN_CHANGE_CANCELLED", admin.accountId())).isOne();
    }

    // ------------------------------------------------------------------ rejections, idempotency, permissions

    @Test
    void invalidChangesAreRejected() {
        String standard = createPlan("Estándar", "49.00", List.of("USERS"), Map.of());
        String premium = createPlan("Premium", "99.00", List.of("USERS"), Map.of());
        String archived = createPlan("Archivado", "59.00", List.of("USERS"), Map.of());
        assertThat(api.post("/api/v1/platform/plans/" + archived + "/archive", admin.token(), Map.of()))
                .hasStatus(HttpStatus.OK);
        UUID clinic = onboard(standard);

        assertCode(requestChange(clinic, standard, "MONTHLY", null, null), HttpStatus.CONFLICT, "PLAN_CHANGE_NOT_ALLOWED");
        assertCode(requestChange(clinic, archived, "MONTHLY", null, null), HttpStatus.CONFLICT, "PLAN_ARCHIVED");
        assertCode(requestChange(clinic, TRIAL_PLAN_ID, "MONTHLY", null, null), HttpStatus.CONFLICT, "PLAN_CHANGE_NOT_ALLOWED");
        assertCode(requestChange(clinic, UUID.randomUUID().toString(), "MONTHLY", null, null), HttpStatus.BAD_REQUEST,
                "VALIDATION");
        assertCode(requestChange(clinic, premium, "MONTHLY", 99L, null), HttpStatus.CONFLICT, "VERSION_CONFLICT");
        assertCode(postPreview(UUID.randomUUID(), premium, "MONTHLY", admin.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");

        assertThat(api.post(TENANTS + "/" + clinic + "/subscription/transitions", admin.token(),
                Map.of("target", "SUSPENDED", "reason", "Prueba", "version", version(getSubscription(clinic)))))
                .hasStatus(HttpStatus.OK);
        assertCode(requestChange(clinic, premium, "MONTHLY", null, null), HttpStatus.CONFLICT, "PLAN_CHANGE_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("select count(*) from plan_change where tenant_id = ?", Integer.class, clinic)).isZero();
    }

    @Test
    void theSameIdempotencyKeyAppliesTheChangeOnce() {
        String standard = createPlan("Estándar", "49.00", List.of("USERS"), Map.of());
        String premium = createPlan("Premium", "99.00", List.of("USERS"), Map.of());
        UUID clinic = onboard(standard);
        String key = UUID.randomUUID().toString();

        Map<String, Object> first = api.body(requestChange(clinic, premium, "MONTHLY", null, key));
        Map<String, Object> retry = api.body(requestChange(clinic, premium, "MONTHLY", null, key));

        assertThat(retry).containsEntry("id", first.get("id"));
        assertThat(jdbc.queryForObject("select count(*) from plan_change where tenant_id = ?", Integer.class, clinic)).isOne();
    }

    @Test
    void onlyTheSuperAdminChangesThePlanOfAClinic() {
        String standard = createPlan("Estándar", "49.00", List.of("USERS"), Map.of());
        String premium = createPlan("Premium", "99.00", List.of("USERS"), Map.of());
        UUID clinic = onboard(standard);
        Clinic other = api.clinic("Clinica Ajena " + UUID.randomUUID().toString().substring(0, 8));

        assertThat(postPreview(clinic, premium, "MONTHLY", other.token())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.post(TENANTS + "/" + clinic + "/subscription/plan-changes", other.token(),
                Map.of("targetPlanId", premium, "billingPeriod", "MONTHLY"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from plan_change where tenant_id = ?", Integer.class, clinic)).isZero();
        assertThat(cast(getSubscription(clinic).get("plan"))).containsEntry("id", standard);
    }

    // ------------------------------------------------------------------ helpers

    private UUID onboard(String planId) {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("fullName", "Ana Prueba");
        owner.put("email", "ana." + marker + "@example.test");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("legalName", "Clínica Cambio " + marker + " S.A.");
        request.put("ruc", String.format("%013d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 10_000_000_000_000L)));
        request.put("clinicType", "MEDICAL");
        request.put("countryCode", "EC");
        request.put("city", "Quito");
        request.put("address", "Avenida Ejemplo 123");
        request.put("phone", "+593000000000");
        request.put("contactEmail", "contacto." + marker + "@example.test");
        request.put("planId", planId);
        request.put("billingPeriod", "MONTHLY");
        request.put("owner", owner);
        MvcTestResult created = api.post(TENANTS, admin.token(), request);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(api.body(created).get("id").toString());
    }

    private String createPlan(String name, String monthly, List<String> modules, Map<String, Object> limits) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("code", "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        plan.put("name", name);
        plan.put("trial", false);
        plan.put("modules", modules);
        plan.put("limits", limits);
        plan.put("prices", List.of(
                Map.of("billingPeriod", "MONTHLY", "currency", "USD", "amount", monthly),
                Map.of("billingPeriod", "ANNUAL", "currency", "USD",
                        "amount", new BigDecimal(monthly).multiply(BigDecimal.TEN).toPlainString())));
        MvcTestResult created = api.post("/api/v1/platform/plans", admin.token(), plan);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private void setPeriod(UUID clinic, LocalDate start, LocalDate end) {
        jdbc.update("update subscription set current_period_start = ?, current_period_end = ? where tenant_id = ?",
                Timestamp.from(startOfDay(start)), Timestamp.from(startOfDay(end)), clinic);
    }

    private Map<String, Object> preview(UUID clinic, String planId, String billingPeriod) {
        MvcTestResult result = postPreview(clinic, planId, billingPeriod, admin.token());
        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        contract.assertResponse(PREVIEW, "POST", 200, "application/json", body);
        return body;
    }

    private MvcTestResult postPreview(UUID clinic, String planId, String billingPeriod, String token) {
        return api.post(TENANTS + "/" + clinic + "/subscription/plan-change-preview", token,
                Map.of("targetPlanId", planId, "billingPeriod", billingPeriod));
    }

    private MvcTestResult requestChange(UUID clinic, String planId, String billingPeriod, Long version, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetPlanId", planId);
        body.put("billingPeriod", billingPeriod);
        if (version != null) {
            body.put("version", version);
        }
        var request = mvc.post().uri(TENANTS + "/" + clinic + "/subscription/plan-changes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request.exchange();
    }

    private Map<String, Object> getSubscription(UUID clinic) {
        MvcTestResult result = api.get(TENANTS + "/" + clinic + "/subscription", admin.token());
        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        contract.assertResponse(SUBSCRIPTION, "GET", 200, "application/json", body);
        return body;
    }

    private long seatedStaff(UUID clinic) {
        return jdbc.queryForObject("""
                select count(*) from identity_user_account
                where tenant_id = ? and kind = 'STAFF' and status in ('INVITED', 'PENDING_EMAIL_VERIFICATION', 'ACTIVE')""",
                Long.class, clinic);
    }

    private int audited(UUID tenantId, String action, UUID actorId) {
        return jdbc.queryForObject("""
                select count(*) from audit_event
                where tenant_id = ? and action = ? and target_type = 'SUBSCRIPTION'
                  and actor_account_id is not distinct from ?""",
                Integer.class, tenantId, action, actorId);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(GUAYAQUIL).toInstant();
    }

    private static long version(Map<String, Object> subscription) {
        return ((Number) subscription.get("version")).longValue();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
