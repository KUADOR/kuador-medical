package com.kuador.medical.platform.offboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import com.kuador.medical.platform.api.TenantDataPurger;
import com.kuador.medical.platform.api.TenantPurgeCancelled;
import com.kuador.medical.platform.api.TenantPurgeRequested;
import com.kuador.medical.platform.api.TenantPurged;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Purge of a clinic (MEDK-70; MEDK-49 CA 13 and 15, plan D6 and D12, ADR-018): a purge before the
 * end of the retention is rejected, the request and the RUC confirmation are audited with who did
 * them, the confirmation checks the status again, a reactivation cancels a pending request, the
 * clinical record purgers are skipped, a clinic never touches another, only the SUPER_ADMIN can
 * purge and neither the RUC nor the reason reach the logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TenantPurgeIT.Purgers.class})
@ExtendWith(OutputCaptureExtension.class)
@RecordApplicationEvents
class TenantPurgeIT {

    private static final String TENANTS = "/api/v1/platform/tenants";
    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");

    /** A purger of each kind, recording the clinics it was called for. */
    @TestConfiguration
    static class Purgers {

        static final List<UUID> ADMINISTRATIVE = new CopyOnWriteArrayList<>();
        static final List<UUID> CLINICAL = new CopyOnWriteArrayList<>();

        @Bean
        TenantDataPurger administrativeTestPurger() {
            return new TenantDataPurger() {
                @Override
                public String name() {
                    return "test.administrative";
                }

                @Override
                public void purge() {
                    ADMINISTRATIVE.add(TenantContext.current().orElseThrow());
                }
            };
        }

        @Bean
        TenantDataPurger clinicalRecordsTestPurger() {
            return new TenantDataPurger() {
                @Override
                public String name() {
                    return "test.clinical-records";
                }

                @Override
                public boolean purgesClinicalRecords() {
                    return true;
                }

                @Override
                public void purge() {
                    CLINICAL.add(TenantContext.current().orElseThrow());
                }
            };
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEvents events;

    private final ContractSchema contract = ContractSchema.platform();
    private IdentityApi api;
    private Admin admin;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
    }

    @Test
    void aPurgeBeforeTheEndOfTheRetentionIsRejectedAndDeletesNothing() {
        Onboarded active = onboard();
        Onboarded cancelled = onboard();
        cancel(cancelled.id());

        assertCode(requestPurge(active.id(), null), HttpStatus.CONFLICT, "PURGE_NOT_ALLOWED");
        MvcTestResult early = requestPurge(cancelled.id(), null);
        assertCode(early, HttpStatus.CONFLICT, "PURGE_NOT_ALLOWED");
        assertThat(api.body(early).get("detail").toString()).startsWith("The retention period ends on ");

        assertThat(count("select count(*) from tenant_purge_request where tenant_id in (?, ?)", active.id(), cancelled.id())).isZero();
        assertThat(api.get(TENANTS + "/" + cancelled.id(), admin.token())).hasStatus(HttpStatus.OK);
        assertThat(events.stream(TenantPurgeRequested.class)).isEmpty();
    }

    @Test
    void theSuperAdminRequestsAndConfirmsWithTheRucAndTheClinicIsPurgedAndAudited() {
        Onboarded clinic = onboard();
        Onboarded bystander = onboard();
        cancel(clinic.id());
        endRetention(clinic.id());
        String key = UUID.randomUUID().toString();

        MvcTestResult requested = requestPurge(clinic.id(), key);
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        Map<String, Object> request = api.body(requested);
        contract.assertResponse("/platform/tenants/{tenantId}/purge-requests", "POST", 201, "application/json", request);
        assertThat(request).containsEntry("status", "REQUESTED").containsEntry("confirmedAt", null);
        assertThat(api.body(requestPurge(clinic.id(), key)).get("id")).as("same Idempotency-Key, same request")
                .isEqualTo(request.get("id"));
        assertCode(requestPurge(clinic.id(), null), HttpStatus.CONFLICT, "PURGE_NOT_ALLOWED");
        assertThat(Purgers.ADMINISTRATIVE).as("nothing is deleted on request").doesNotContain(clinic.id());

        UUID requestId = UUID.fromString(request.get("id").toString());
        MvcTestResult mismatch = confirm(clinic.id(), requestId, bystander.ruc());
        assertCode(mismatch, HttpStatus.BAD_REQUEST, "VALIDATION");
        assertThat(mismatch).bodyJson().hasPathSatisfying("$.errors[0].field",
                field -> assertThat(field).asString().isEqualTo("rucConfirmation"));
        assertThat(mismatch).bodyJson().hasPathSatisfying("$.errors[0].code",
                code -> assertThat(code).asString().isEqualTo("MISMATCH"));

        MvcTestResult confirmed = confirm(clinic.id(), requestId, clinic.ruc());
        assertThat(confirmed).hasStatus(HttpStatus.OK);
        Map<String, Object> done = api.body(confirmed);
        contract.assertResponse("/platform/tenants/{tenantId}/purge-requests/{requestId}/confirm", "POST", 200,
                "application/json", done);
        assertThat(done).containsEntry("status", "CONFIRMED");
        assertThat(done.get("confirmedAt")).isNotNull();

        Map<String, Object> tenant = jdbc.queryForMap(
                "select purged_at, owner_email, owner_full_name, contact_email, phone, address from tenant where id = ?",
                clinic.id());
        assertThat(tenant.get("purged_at")).isNotNull();
        assertThat(tenant).containsEntry("owner_email", null).containsEntry("owner_full_name", null)
                .containsEntry("contact_email", null).containsEntry("phone", null).containsEntry("address", null);
        assertThat(api.get(TENANTS + "/" + clinic.id(), admin.token())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(confirm(clinic.id(), requestId, clinic.ruc())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(Purgers.ADMINISTRATIVE).containsOnlyOnce(clinic.id()).doesNotContain(bystander.id());
        assertThat(Purgers.CLINICAL).as("clinical records are not purged until the legal validation (ADR-018)")
                .doesNotContain(clinic.id());

        assertThat(audited(clinic.id(), "TENANT_PURGE_REQUESTED", admin.accountId())).isOne();
        assertThat(audited(clinic.id(), "TENANT_PURGE_CONFIRMED", admin.accountId())).isOne();
        assertThat(audited(clinic.id(), "TENANT_PURGED", admin.accountId())).isOne();
        assertThat(events.stream(TenantPurgeRequested.class).filter(e -> e.tenantId().equals(clinic.id()))).hasSize(1);
        assertThat(events.stream(TenantPurged.class).filter(e -> e.tenantId().equals(clinic.id())))
                .singleElement().satisfies(e -> assertThat(e.purgeRequestId()).isEqualTo(requestId));

        assertThat(jdbc.queryForObject("select purged_at from tenant where id = ?", Timestamp.class, bystander.id()))
                .as("another clinic is never touched").isNull();
        assertThat(api.get(TENANTS + "/" + bystander.id(), admin.token())).hasStatus(HttpStatus.OK);
    }

    @Test
    void aReactivationCancelsThePendingRequestAndItCanNoLongerBeConfirmed() {
        Onboarded clinic = onboard();
        Map<String, Object> cancelled = cancel(clinic.id());
        UUID requestId = UUID.randomUUID();
        jdbc.update("""
                insert into tenant_purge_request (id, tenant_id, status, reason, requested_by, requested_at)
                values (?, ?, 'REQUESTED', 'Pedido pendiente', ?, now())""", requestId, clinic.id(), admin.accountId());

        assertThat(api.post(TENANTS + "/" + clinic.id() + "/subscription/transitions", admin.token(),
                Map.of("target", "ACTIVE", "reason", "El cliente volvió", "version", cancelled.get("version"))))
                .hasStatus(HttpStatus.OK);

        assertThat(jdbc.queryForObject("select status from tenant_purge_request where id = ?", String.class, requestId))
                .isEqualTo("CANCELLED");
        assertThat(audited(clinic.id(), "TENANT_PURGE_CANCELLED", null)).isOne();
        assertThat(events.stream(TenantPurgeCancelled.class).filter(e -> e.purgeRequestId().equals(requestId))).hasSize(1);
        assertCode(confirm(clinic.id(), requestId, clinic.ruc()), HttpStatus.CONFLICT, "PURGE_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("select purged_at from tenant where id = ?", Timestamp.class, clinic.id())).isNull();
    }

    @Test
    void theConfirmationChecksTheStatusAgain() {
        Onboarded clinic = onboard();
        cancel(clinic.id());
        endRetention(clinic.id());
        UUID requestId = UUID.fromString(api.body(requestPurge(clinic.id(), null)).get("id").toString());
        jdbc.update("update subscription set status = 'SUSPENDED' where tenant_id = ?", clinic.id());

        assertCode(confirm(clinic.id(), requestId, clinic.ruc()), HttpStatus.CONFLICT, "PURGE_NOT_ALLOWED");

        assertThat(jdbc.queryForObject("select status from tenant_purge_request where id = ?", String.class, requestId))
                .isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("select purged_at from tenant where id = ?", Timestamp.class, clinic.id())).isNull();
        assertThat(Purgers.ADMINISTRATIVE).doesNotContain(clinic.id());
    }

    @Test
    void onlyTheSuperAdminPurgesAndARequestOfOneClinicIsNotFoundInAnother() {
        Onboarded clinicA = onboard();
        Onboarded clinicB = onboard();
        cancel(clinicA.id());
        endRetention(clinicA.id());
        cancel(clinicB.id());
        endRetention(clinicB.id());
        UUID requestA = UUID.fromString(api.body(requestPurge(clinicA.id(), null)).get("id").toString());
        IdentityApi.Clinic clinicUser = api.clinic("Clinica Curiosa " + UUID.randomUUID().toString().substring(0, 8));

        assertThat(api.post(TENANTS + "/" + clinicA.id() + "/purge-requests", clinicUser.token(),
                Map.of("reason", "Intento"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.post(TENANTS + "/" + clinicA.id() + "/purge-requests/" + requestA + "/confirm",
                clinicUser.token(), Map.of("rucConfirmation", clinicA.ruc()))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(confirm(clinicB.id(), requestA, clinicB.ruc())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(requestPurge(UUID.randomUUID(), null)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select status from tenant_purge_request where id = ?", String.class, requestA))
                .isEqualTo("REQUESTED");
    }

    @Test
    void neitherTheRucNorTheReasonReachTheLogs(CapturedOutput output) {
        Onboarded clinic = onboard();
        cancel(clinic.id());
        endRetention(clinic.id());
        UUID requestId = UUID.fromString(api.body(api.post(TENANTS + "/" + clinic.id() + "/purge-requests", admin.token(),
                Map.of("reason", "Motivo reservado de la baja"))).get("id").toString());

        confirm(clinic.id(), requestId, "0000000000000");
        confirm(clinic.id(), requestId, clinic.ruc());

        assertThat(output.getAll()).doesNotContain(clinic.ruc()).doesNotContain("Motivo reservado de la baja")
                .doesNotContain(clinic.ownerEmail()).doesNotContain(clinic.legalName());
    }

    // ------------------------------------------------------------------ helpers

    private record Onboarded(UUID id, String ruc, String ownerEmail, String legalName) {}

    private Onboarded onboard() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("fullName", "Ana Prueba");
        owner.put("email", "ana." + marker + "@example.test");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("legalName", "Clínica Baja " + marker + " S.A.");
        request.put("ruc", String.format("%013d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 10_000_000_000_000L)));
        request.put("clinicType", "MEDICAL");
        request.put("countryCode", "EC");
        request.put("city", "Quito");
        request.put("address", "Avenida Ejemplo 123");
        request.put("phone", "+593000000000");
        request.put("contactEmail", "contacto." + marker + "@example.test");
        request.put("planId", createPaidPlan());
        request.put("billingPeriod", "MONTHLY");
        request.put("owner", owner);
        MvcTestResult created = api.post(TENANTS, admin.token(), request);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return new Onboarded(UUID.fromString(api.body(created).get("id").toString()), request.get("ruc").toString(),
                owner.get("email").toString(), request.get("legalName").toString());
    }

    private String createPaidPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("code", "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        plan.put("name", "Estándar");
        plan.put("trial", false);
        plan.put("modules", List.of("AGENDA", "PATIENTS", "USERS"));
        plan.put("limits", Map.of("MAX_USERS", 15));
        plan.put("prices", List.of(Map.of("billingPeriod", "MONTHLY", "currency", "USD", "amount", "49.00")));
        MvcTestResult created = api.post("/api/v1/platform/plans", admin.token(), plan);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    /** Cancels the clinic now (manual transition): its retention ends in RETENTION_DAYS. */
    private Map<String, Object> cancel(UUID tenantId) {
        Map<String, Object> current = api.body(api.get(TENANTS + "/" + tenantId + "/subscription", admin.token()));
        MvcTestResult cancelled = api.post(TENANTS + "/" + tenantId + "/subscription/transitions", admin.token(),
                Map.of("target", "CANCELLED", "version", current.get("version")));
        assertThat(cancelled).hasStatus(HttpStatus.OK);
        return api.body(cancelled);
    }

    /** The retention ended yesterday. */
    private void endRetention(UUID tenantId) {
        Instant yesterday = LocalDate.now(GUAYAQUIL).minusDays(1).atStartOfDay(GUAYAQUIL).toInstant();
        jdbc.update("update subscription set retention_ends_at = ? where tenant_id = ?", Timestamp.from(yesterday), tenantId);
    }

    private MvcTestResult requestPurge(UUID tenantId, String idempotencyKey) {
        Map<String, Object> body = Map.of("reason", "Retención vencida; la clínica descargó sus datos.");
        if (idempotencyKey == null) {
            return api.post(TENANTS + "/" + tenantId + "/purge-requests", admin.token(), body);
        }
        return mvc.post().uri(TENANTS + "/" + tenantId + "/purge-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(body))
                .exchange();
    }

    private MvcTestResult confirm(UUID tenantId, UUID requestId, String ruc) {
        return api.post(TENANTS + "/" + tenantId + "/purge-requests/" + requestId + "/confirm", admin.token(),
                Map.of("rucConfirmation", ruc));
    }

    private int audited(UUID tenantId, String action, UUID actorId) {
        return count("""
                select count(*) from audit_event
                where tenant_id = ? and action = ? and actor_account_id is not distinct from ?""",
                tenantId, action, actorId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }
}
