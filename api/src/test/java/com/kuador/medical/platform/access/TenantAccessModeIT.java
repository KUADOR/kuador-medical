package com.kuador.medical.platform.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.StaffFixture;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * The plan seen and enforced from the clinic side (MEDK-69; MEDK-49 CA 9, 12 and 17, ADR-017):
 * {@code GET /tenant/subscription}, the read-only mode of a SUSPENDED or CANCELLED clinic
 * ({@code TENANT_READ_ONLY}, nothing deleted), modules outside the plan ({@code MODULE_NOT_IN_PLAN}),
 * the user limit ({@code PLAN_LIMIT_REACHED}), the {@code subscription.read} permission and the
 * isolation between clinics A and B. Clinics are signed up and given a subscription directly, as
 * the platform onboarding would leave it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantAccessModeIT {

    private static final String CLINIC_SUBSCRIPTION = "/api/v1/tenant/subscription";
    private static final String SETTINGS = "/api/v1/patient-registration-settings";
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
    private StaffFixture staff;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
        staff = new StaffFixture(jdbc, passwordEncoder);
    }

    @Test
    void eachClinicReadsOnlyItsOwnSubscriptionWithModulesLimitsAndUsage() {
        String standard = createPlan("Estándar", List.of("AGENDA", "PATIENTS", "USERS"),
                Map.of("MAX_USERS", 15, "MAX_BRANCHES", 3));
        String other = createPlan("Otro", List.of("USERS"), Map.of());
        Clinic a = api.clinic("Clinica A " + marker());
        Clinic b = api.clinic("Clinica B " + marker());
        subscribe(a.tenantId(), standard, "ACTIVE");
        subscribe(b.tenantId(), other, "ACTIVE");

        Map<String, Object> mine = clinicSubscription(a.token());

        assertThat(mine).containsEntry("status", "ACTIVE").containsEntry("accessMode", "FULL")
                .containsEntry("priceAmount", "49.00").containsEntry("currency", "USD")
                .containsEntry("modules", List.of("AGENDA", "PATIENTS", "USERS"))
                .containsEntry("scheduledPlanChange", null).containsEntry("notices", List.of());
        assertThat(cast(mine.get("plan"))).containsEntry("id", standard);
        assertThat(maps(mine.get("limits"))).extracting(limit -> limit.get("limit"))
                .containsExactly("MAX_BRANCHES", "MAX_USERS", "MAX_PATIENTS", "MAX_APPOINTMENTS", "MAX_STORAGE_MB");
        assertThat(limit(mine, "MAX_USERS")).containsEntry("limitValue", 15)
                .containsEntry("usage", (int) seatedStaff(a.tenantId()));
        assertThat(limit(mine, "MAX_BRANCHES")).containsEntry("limitValue", 3)
                .containsEntry("usage", (int) branches(a.tenantId()));
        assertThat(limit(mine, "MAX_PATIENTS")).as("no module reports it yet")
                .containsEntry("limitValue", null).containsEntry("usage", null);

        Map<String, Object> theirs = clinicSubscription(b.token());
        assertThat(cast(theirs.get("plan"))).as("clinic B never sees the plan of A").containsEntry("id", other);
        assertThat(api.get(CLINIC_SUBSCRIPTION, admin.token())).as("the platform has no clinic subscription")
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.get(CLINIC_SUBSCRIPTION, api.clinic("Sin suscripción " + marker()).token()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aSuspendedClinicReadsButCannotWriteWhileAnActiveOneCan() {
        String standard = createPlan("Estándar", List.of("AGENDA", "PATIENTS", "USERS"), Map.of());
        Clinic suspended = api.clinic("Clinica Suspendida " + marker());
        Clinic active = api.clinic("Clinica Activa " + marker());
        subscribe(suspended.tenantId(), standard, "SUSPENDED");
        subscribe(active.tenantId(), standard, "ACTIVE");
        long seats = seatedStaff(suspended.tenantId());

        MvcTestResult settings = api.get(SETTINGS, suspended.token());
        assertThat(settings).as("reads keep answering").hasStatus(HttpStatus.OK);
        assertCode(api.put(SETTINGS, suspended.token(), Map.of("selfRegistrationEnabled", true,
                "version", api.body(settings).get("version"))), HttpStatus.FORBIDDEN, "TENANT_READ_ONLY");
        assertCode(api.post("/api/v1/staff-users", suspended.token(), newStaff(suspended.tenantId())), HttpStatus.FORBIDDEN, "TENANT_READ_ONLY");
        assertThat(seatedStaff(suspended.tenantId())).isEqualTo(seats);
        assertThat(api.body(api.get(SETTINGS, suspended.token()))).containsEntry("selfRegistrationEnabled", false);

        Map<String, Object> view = clinicSubscription(suspended.token());
        assertThat(view).containsEntry("status", "SUSPENDED").containsEntry("accessMode", "READ_ONLY");
        assertThat(maps(view.get("notices"))).extracting(notice -> notice.get("kind")).containsExactly("SUSPENDED");

        MvcTestResult otherSettings = api.get(SETTINGS, active.token());
        assertThat(api.put(SETTINGS, active.token(), Map.of("selfRegistrationEnabled", true,
                "version", api.body(otherSettings).get("version")))).as("clinic B is not affected").hasStatus(HttpStatus.OK);
    }

    @Test
    void aCancelledClinicWithinRetentionIsReadOnlyToo() {
        String standard = createPlan("Estándar", List.of("AGENDA", "PATIENTS", "USERS"), Map.of());
        Clinic cancelled = api.clinic("Clinica Cancelada " + marker());
        subscribe(cancelled.tenantId(), standard, "CANCELLED");

        assertThat(api.get(SETTINGS, cancelled.token())).hasStatus(HttpStatus.OK);
        assertCode(api.post("/api/v1/staff-users", cancelled.token(), newStaff(cancelled.tenantId())), HttpStatus.FORBIDDEN, "TENANT_READ_ONLY");
        assertThat(clinicSubscription(cancelled.token())).containsEntry("accessMode", "RETENTION");
    }

    @Test
    void aModuleOutsideThePlanIsRejectedAndTheOthersAnswer() {
        String usersOnly = createPlan("Solo usuarios", List.of("USERS"), Map.of());
        Clinic clinic = api.clinic("Clinica Sin Pacientes " + marker());
        subscribe(clinic.tenantId(), usersOnly, "ACTIVE");

        assertCode(api.get("/api/v1/patients", clinic.token()), HttpStatus.FORBIDDEN, "MODULE_NOT_IN_PLAN");
        assertCode(api.get(SETTINGS, clinic.token()), HttpStatus.FORBIDDEN, "MODULE_NOT_IN_PLAN");
        assertThat(api.get("/api/v1/staff-users", clinic.token())).hasStatus(HttpStatus.OK);
    }

    @Test
    void staffBeyondThePlanLimitAreRejectedWithoutDeletingAnyone() {
        Clinic clinic = api.clinic("Clinica Llena " + marker());
        long seats = seatedStaff(clinic.tenantId());
        String full = createPlan("Lleno", List.of("USERS", "PATIENTS"), Map.of("MAX_USERS", (int) seats));
        subscribe(clinic.tenantId(), full, "ACTIVE");

        assertCode(api.post("/api/v1/staff-users", clinic.token(), newStaff(clinic.tenantId())), HttpStatus.CONFLICT, "PLAN_LIMIT_REACHED");

        assertThat(seatedStaff(clinic.tenantId())).isEqualTo(seats);
        assertThat(limit(clinicSubscription(clinic.token()), "MAX_USERS"))
                .containsEntry("limitValue", (int) seats).containsEntry("usage", (int) seats);
    }

    @Test
    void readingTheSubscriptionRequiresSubscriptionRead() {
        String standard = createPlan("Estándar", List.of("USERS", "PATIENTS"), Map.of());
        Clinic clinic = api.clinic("Clinica Permisos " + marker());
        subscribe(clinic.tenantId(), standard, "ACTIVE");
        String email = IdentityApi.uniqueEmail("reception");
        String password = IdentityApi.randomPassword();
        UUID user = staff.staffUser(clinic.tenantId(), email, password);
        staff.assign(clinic.tenantId(), user, staff.branch(clinic.tenantId(), "Matriz " + marker()),
                staff.role(clinic.tenantId(), "RECEPTION"));

        assertCode(api.get(CLINIC_SUBSCRIPTION, api.token(email, password)), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    // ------------------------------------------------------------------ helpers

    private String createPlan(String name, List<String> modules, Map<String, Object> limits) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("code", "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        plan.put("name", name);
        plan.put("trial", false);
        plan.put("modules", modules);
        plan.put("limits", limits);
        plan.put("prices", List.of(Map.of("billingPeriod", "MONTHLY", "currency", "USD", "amount", "49.00")));
        MvcTestResult created = api.post("/api/v1/platform/plans", admin.token(), plan);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    /** The subscription the platform onboarding would leave, in {@code status}. */
    private void subscribe(UUID tenantId, String planId, String status) {
        LocalDate today = LocalDate.now(GUAYAQUIL);
        Instant start = startOfDay(today.minusDays(10));
        Instant end = startOfDay(today.plusDays(20));
        jdbc.update("""
                insert into subscription
                    (id, tenant_id, plan_id, billing_period, status, price_amount_snapshot, price_currency_snapshot,
                     current_period_start, current_period_end, suspended_at, cancelled_at, retention_ends_at)
                values (?, ?, ?, 'MONTHLY', ?, 49.00, 'USD', ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tenantId, UUID.fromString(planId), status, Timestamp.from(start), Timestamp.from(end),
                "SUSPENDED".equals(status) ? Timestamp.from(start) : null,
                "CANCELLED".equals(status) ? Timestamp.from(start) : null,
                "CANCELLED".equals(status) ? Timestamp.from(startOfDay(today.plusDays(80))) : null);
    }

    private Map<String, Object> clinicSubscription(String token) {
        MvcTestResult result = api.get(CLINIC_SUBSCRIPTION, token);
        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        contract.assertResponse("/tenant/subscription", "GET", 200, "application/json", body);
        return body;
    }

    private Map<String, Object> newStaff(UUID tenantId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("firstNames", "Jane");
        request.put("lastNames", "Doe");
        request.put("email", IdentityApi.uniqueEmail("staff"));
        request.put("jobTitle", "Front desk");
        request.put("roleAssignments", Map.of("mode", "ALL_BRANCHES", "roleId", staff.role(tenantId, "RECEPTION").toString()));
        return request;
    }

    private long seatedStaff(UUID clinic) {
        return jdbc.queryForObject("""
                select count(*) from identity_user_account
                where tenant_id = ? and kind = 'STAFF' and status in ('INVITED', 'PENDING_EMAIL_VERIFICATION', 'ACTIVE')""",
                Long.class, clinic);
    }

    private long branches(UUID clinic) {
        return jdbc.queryForObject("select count(*) from clinics_branch where tenant_id = ?", Long.class, clinic);
    }

    private static Map<String, Object> limit(Map<String, Object> subscription, String code) {
        return maps(subscription.get("limits")).stream().filter(limit -> code.equals(limit.get("limit"))).findFirst()
                .orElseThrow();
    }

    private static String marker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(GUAYAQUIL).toInstant();
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
