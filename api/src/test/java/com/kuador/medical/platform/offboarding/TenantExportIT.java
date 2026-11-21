package com.kuador.medical.platform.offboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.StaffFixture;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Data export of a clinic (MEDK-70; MEDK-49 FR-021, CA 12 and 17, ADR-018): JSON and CSV files with
 * the clinic's own data only, allowed while the clinic is read-only, signed short-lived download
 * links, audit of who exported and who downloaded, the {@code tenant.data.export} permission,
 * isolation between clinics A and B and expiry of the files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TenantExportIT {

    private static final String EXPORTS = "/api/v1/tenant/exports";
    private static final String WEB_BASE_URL = "https://app.example.test";
    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantExportCleanupJob cleanup;

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
    void theOwnerExportsItsClinicAsJsonWithoutAnyDataOfAnotherClinicAndItIsAudited() throws IOException {
        String plan = createPlan();
        String nameA = "Clinica Exporta A " + marker();
        String nameB = "Clinica Exporta B " + marker();
        Clinic a = api.clinic(nameA);
        Clinic b = api.clinic(nameB);
        subscribe(a.tenantId(), plan, "ACTIVE");
        subscribe(b.tenantId(), plan, "ACTIVE");
        String key = UUID.randomUUID().toString();

        MvcTestResult accepted = requestExport(a.token(), "JSON", key);

        assertThat(accepted).hasStatus(HttpStatus.ACCEPTED);
        Map<String, Object> created = api.body(accepted);
        contract.assertResponse("/tenant/exports", "POST", 202, "application/json", created);
        String exportId = created.get("id").toString();
        assertThat(accepted.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/tenant/exports/" + exportId);
        assertThat(api.body(requestExport(a.token(), "JSON", key)).get("id")).as("same key, same export").isEqualTo(exportId);
        assertThat(jdbc.queryForObject("select count(*) from tenant_export where tenant_id = ?", Integer.class, a.tenantId()))
                .isOne();

        Map<String, Object> ready = getExport(a.token(), exportId);
        assertThat(ready).containsEntry("status", "READY").containsEntry("format", "JSON");
        assertThat(ready.get("expiresAt")).isNotNull();
        String downloadUrl = ready.get("downloadUrl").toString();
        assertThat(downloadUrl).startsWith(WEB_BASE_URL + "/api/v1/public/tenant-exports/download?grant=")
                .doesNotContain(a.tenantId().toString());

        MvcTestResult file = download(downloadUrl);
        assertThat(file).hasStatus(HttpStatus.OK);
        assertThat(file.getResponse().getContentType()).startsWith("application/json");
        assertThat(file.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;")
                .contains(".json");
        assertThat(file.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        String content = file.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).contains(nameA).doesNotContain(nameB).doesNotContain(b.tenantId().toString());
        Map<String, Object> document = jsonMapper.readValue(content, Map.class);
        assertThat(document).containsEntry("clinicId", a.tenantId().toString());
        Map<String, Object> sections = cast(document.get("sections"));
        assertThat(sections).containsKeys("platform.clinic", "platform.subscription",
                "platform.subscription.periods", "platform.subscription.transitions");
        assertThat(maps(sections.get("platform.subscription"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("status", "ACTIVE").containsEntry("priceAmount", "49.00"));

        UUID requestedBy = jdbc.queryForObject("select requested_by from tenant_export where id = ?", UUID.class,
                UUID.fromString(exportId));
        assertThat(requestedBy).isEqualTo(staff.ownerOf(a.tenantId()));
        assertThat(audited(a.tenantId(), "TENANT_EXPORT_REQUESTED", requestedBy)).isOne();
        assertThat(audited(a.tenantId(), "TENANT_EXPORT_DOWNLOADED", requestedBy)).isOne();
        assertThat(audited(b.tenantId(), "TENANT_EXPORT_REQUESTED", null)
                + audited(b.tenantId(), "TENANT_EXPORT_REQUESTED", requestedBy)).isZero();
    }

    @Test
    void aCsvExportIsAZipWithOneCsvFilePerSection() throws IOException {
        Clinic clinic = api.clinic("Clinica Csv " + marker());
        subscribe(clinic.tenantId(), createPlan(), "ACTIVE");

        String exportId = api.body(requestExport(clinic.token(), "CSV", null)).get("id").toString();
        MvcTestResult file = download(getExport(clinic.token(), exportId).get("downloadUrl").toString());

        assertThat(file).hasStatus(HttpStatus.OK);
        assertThat(file.getResponse().getContentType()).isEqualTo("application/zip");
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file.getResponse().getContentAsByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsKeys("platform.clinic.csv", "platform.subscription.csv");
        assertThat(entries.get("platform.subscription.csv")).contains("status").contains("ACTIVE");
    }

    @Test
    void aSuspendedOrCancelledClinicStillExportsItsData() {
        String plan = createPlan();
        Clinic suspended = api.clinic("Clinica Suspendida " + marker());
        Clinic cancelled = api.clinic("Clinica Cancelada " + marker());
        subscribe(suspended.tenantId(), plan, "SUSPENDED");
        subscribe(cancelled.tenantId(), plan, "CANCELLED");

        MvcTestResult fromSuspended = requestExport(suspended.token(), "JSON", null);
        MvcTestResult fromCancelled = requestExport(cancelled.token(), "CSV", null);

        assertThat(fromSuspended).hasStatus(HttpStatus.ACCEPTED);
        assertThat(fromCancelled).hasStatus(HttpStatus.ACCEPTED);
        assertThat(api.body(fromCancelled)).containsEntry("status", "READY");
    }

    @Test
    void anotherClinicNeitherSeesTheExportNorDownloadsWithATamperedLink() {
        String plan = createPlan();
        Clinic a = api.clinic("Clinica A " + marker());
        Clinic b = api.clinic("Clinica B " + marker());
        subscribe(a.tenantId(), plan, "ACTIVE");
        subscribe(b.tenantId(), plan, "ACTIVE");
        String exportId = api.body(requestExport(a.token(), "JSON", null)).get("id").toString();
        String url = getExport(a.token(), exportId).get("downloadUrl").toString();

        assertThat(api.get(EXPORTS + "/" + exportId, b.token())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.get(EXPORTS + "/" + UUID.randomUUID(), a.token())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(download(url.substring(0, url.length() - 3) + "abc")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/public/tenant-exports/download").param("grant", "x.y").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(audited(a.tenantId(), "TENANT_EXPORT_DOWNLOADED", null)
                + jdbc.queryForObject("select count(*) from audit_event where action = 'TENANT_EXPORT_DOWNLOADED' and tenant_id = ?",
                        Integer.class, a.tenantId())).isZero();
        assertThat(api.get(EXPORTS + "/" + exportId, admin.token())).as("the platform has no clinic export")
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void exportingRequiresTenantDataExport() {
        Clinic clinic = api.clinic("Clinica Permisos " + marker());
        subscribe(clinic.tenantId(), createPlan(), "ACTIVE");
        String email = IdentityApi.uniqueEmail("reception");
        String password = IdentityApi.randomPassword();
        UUID user = staff.staffUser(clinic.tenantId(), email, password);
        staff.assign(clinic.tenantId(), user, staff.branch(clinic.tenantId(), "Matriz " + marker()),
                staff.role(clinic.tenantId(), "RECEPTION"));

        assertCode(requestExport(api.token(email, password), "JSON", null), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(jdbc.queryForObject("select count(*) from tenant_export where tenant_id = ?", Integer.class,
                clinic.tenantId())).isZero();
    }

    @Test
    void anExpiredExportHasNoLinkAndTheCleanupDropsItsFile() {
        Clinic clinic = api.clinic("Clinica Vencida " + marker());
        subscribe(clinic.tenantId(), createPlan(), "ACTIVE");
        String exportId = api.body(requestExport(clinic.token(), "JSON", null)).get("id").toString();
        String url = getExport(clinic.token(), exportId).get("downloadUrl").toString();
        jdbc.update("update tenant_export set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), UUID.fromString(exportId));

        assertThat(getExport(clinic.token(), exportId)).containsEntry("status", "EXPIRED").containsEntry("downloadUrl", null);
        assertThat(download(url)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(cleanup.runOnce(Instant.now())).isPositive();
        assertThat(jdbc.queryForMap("select status, content from tenant_export where id = ?", UUID.fromString(exportId)))
                .containsEntry("status", "EXPIRED").containsEntry("content", null);
    }

    @Test
    void aPurgedClinicCannotDownloadAndNoDataReachesTheLogs(CapturedOutput output) {
        String name = "Clinica Reservada " + marker();
        Clinic clinic = api.clinic(name);
        subscribe(clinic.tenantId(), createPlan(), "CANCELLED");
        String exportId = api.body(requestExport(clinic.token(), "JSON", null)).get("id").toString();
        String url = getExport(clinic.token(), exportId).get("downloadUrl").toString();
        jdbc.update("update tenant set purged_at = now() where id = ?", clinic.tenantId());

        assertThat(download(url)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(output.getAll()).doesNotContain(name).doesNotContain(url.substring(url.indexOf("grant=") + 6));
    }

    // ------------------------------------------------------------------ helpers

    private MvcTestResult requestExport(String token, String format, String idempotencyKey) {
        var request = mvc.post().uri(EXPORTS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(Map.of("format", format)));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return request.exchange();
    }

    private Map<String, Object> getExport(String token, String exportId) {
        MvcTestResult result = api.get(EXPORTS + "/" + exportId, token);
        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        contract.assertResponse("/tenant/exports/{exportId}", "GET", 200, "application/json", body);
        return body;
    }

    /** Opens the link as a browser would: no access token. */
    private MvcTestResult download(String url) {
        assertThat(url).startsWith(WEB_BASE_URL);
        return mvc.get().uri(url.substring(WEB_BASE_URL.length())).exchange();
    }

    private String createPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("code", "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        plan.put("name", "Estándar");
        plan.put("trial", false);
        plan.put("modules", List.of("AGENDA", "PATIENTS", "USERS"));
        plan.put("limits", Map.of());
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

    private int audited(UUID tenantId, String action, UUID actorId) {
        return jdbc.queryForObject("""
                select count(*) from audit_event
                where tenant_id = ? and action = ? and target_type = 'TENANT_EXPORT'
                  and actor_account_id is not distinct from ?""",
                Integer.class, tenantId, action, actorId);
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
