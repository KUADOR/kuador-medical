package com.kuador.medical.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plans, prices, catalogs and settings are SUPER_ADMIN only (MEDK-49 CA 2, Constitution II and
 * VII): every clinic role, OWNER and ADMIN included, gets 403 and changes nothing; no clinic role
 * can be given a platform permission.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformPermissionsIT {

    private static final String TRIAL_PLAN_ID = "6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
    }

    private List<Map.Entry<String, Function<String, MvcTestResult>>> platformOperations() {
        String plan = "/api/v1/platform/plans/" + TRIAL_PLAN_ID;
        Map<String, Object> planBody = Map.of("code", "FORBIDDEN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "name", "Prohibido", "trial", false, "modules", List.of(), "limits", Map.of());
        Map<String, Object> settings = Map.of("trialDays", 1, "graceDays", 1, "suspensionDaysBeforeCancel", 1,
                "retentionDays", 1, "ownerInvitationTtlHours", 1, "priceChangeNoticeDays", 1, "version", 0);
        return List.of(
                Map.entry("GET /platform/plans", token -> api.get("/api/v1/platform/plans", token)),
                Map.entry("POST /platform/plans", token -> api.post("/api/v1/platform/plans", token, planBody)),
                Map.entry("GET /platform/plans/{planId}", token -> api.get(plan, token)),
                Map.entry("PUT /platform/plans/{planId}", token -> api.put(plan, token,
                        Map.of("name", "X", "modules", List.of(), "limits", Map.of(), "version", 0))),
                Map.entry("POST /platform/plans/{planId}/archive", token -> api.post(plan + "/archive", token, Map.of())),
                Map.entry("GET /platform/plans/{planId}/prices", token -> api.get(plan + "/prices", token)),
                Map.entry("POST /platform/plans/{planId}/prices", token -> api.post(plan + "/prices", token,
                        Map.of("billingPeriod", "MONTHLY", "amount", "1.00", "effectiveFrom", "2099-01-01T00:00:00Z"))),
                Map.entry("GET /platform/catalog/modules", token -> api.get("/api/v1/platform/catalog/modules", token)),
                Map.entry("GET /platform/catalog/limits", token -> api.get("/api/v1/platform/catalog/limits", token)),
                Map.entry("GET /platform/settings", token -> api.get("/api/v1/platform/settings", token)),
                Map.entry("PUT /platform/settings", token -> api.put("/api/v1/platform/settings", token, settings)));
    }

    @Test
    void clinicOwnerAndAdminAreForbiddenOnEveryPlatformOperation() {
        Clinic clinic = api.clinic("Clinica Sin Planes");
        assertThat(api.body(api.me(clinic.token())).get("roles").toString()).contains("OWNER");
        int plansBefore = count("plan");
        int pricesBefore = count("plan_price");
        String settingsBefore = settingsSnapshot();

        for (var operation : platformOperations()) {
            MvcTestResult result = operation.getValue().apply(clinic.token());
            assertThat(result).as(operation.getKey()).hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("FORBIDDEN"));
            String[] route = operation.getKey().split(" ");
            ContractSchema.platform().assertResponse(route[1], route[0], 403, "application/problem+json", api.body(result));
        }

        assertThat(count("plan")).isEqualTo(plansBefore);
        assertThat(count("plan_price")).isEqualTo(pricesBefore);
        assertThat(settingsSnapshot()).isEqualTo(settingsBefore);
        assertThat(jdbc.queryForObject("select status from plan where id = ?::uuid", String.class, TRIAL_PLAN_ID))
                .isEqualTo("ACTIVE");
    }

    @Test
    void anonymousCallerIsUnauthenticated() {
        for (var operation : platformOperations()) {
            String[] route = operation.getKey().split(" ");
            String uri = "/api/v1" + route[1].replace("{planId}", TRIAL_PLAN_ID);
            MvcTestResult result = switch (route[0]) {
                case "GET" -> mvc.get().uri(uri).exchange();
                case "PUT" -> mvc.put().uri(uri).exchange();
                default -> mvc.post().uri(uri).exchange();
            };
            assertThat(result).as(operation.getKey()).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void noClinicRoleCanBeGivenAPlatformPermission() {
        Clinic clinic = api.clinic("Clinica Escalada");

        for (Permission permission : Permission.ofScope(PermissionScope.PLATFORM)) {
            MvcTestResult result = api.post("/api/v1/roles", clinic.token(),
                    Map.of("name", "Plataforma " + UUID.randomUUID(), "permissions", List.of(permission.code())));
            assertThat(result).as(permission.code()).hasStatus(HttpStatus.BAD_REQUEST);
        }
        assertThat(api.bodyList(api.get("/api/v1/permissions", clinic.token())))
                .extracting(permission -> permission.get("code").toString())
                .noneMatch(code -> code.startsWith("platform."));
        assertThat(jdbc.queryForObject(
                "select count(*) from identity_role_permission where permission_code like 'platform.%'", Integer.class))
                .isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String settingsSnapshot() {
        return jdbc.queryForList("select key || '=' || value || '@' || version from platform_setting order by key", String.class)
                .toString();
    }
}
