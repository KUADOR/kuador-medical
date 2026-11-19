package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Explicit permission on every role and catalog operation (FR-007, FR-014, SC-003): without the
 * permission the API answers 403; the SUPER_ADMIN is forbidden on clinic operations and clinic
 * staff on platform operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RolePermissionIT {

    private static final Map<String, Object> ROLE_BODY =
            Map.of("name", "Reception", "permissions", List.of("roles.read"), "version", 0);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private IdentityApi api;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
    }

    /** One call per clinic operation, given a token and the id of an existing custom role. */
    private List<Map.Entry<String, Function<String, MvcTestResult>>> clinicOperations(String roleId) {
        return List.of(
                Map.entry("roles.read", token -> api.get("/api/v1/permissions", token)),
                Map.entry("roles.read", token -> api.get("/api/v1/roles", token)),
                Map.entry("roles.read", token -> api.get("/api/v1/roles/" + roleId, token)),
                Map.entry("roles.create", token -> api.post("/api/v1/roles", token,
                        Map.of("name", "New " + UUID.randomUUID(), "permissions", List.of()))),
                Map.entry("roles.update", token -> api.put("/api/v1/roles/" + roleId, token, ROLE_BODY)),
                Map.entry("roles.delete", token -> api.delete("/api/v1/roles/" + roleId, token)));
    }

    @Test
    void withoutAnyRolePermissionEveryClinicOperationIsForbidden() {
        Clinic clinic = api.clinic("Clinica Sin Permisos");
        String roleId = createRole(clinic);
        revoke(clinic, "roles.read", "roles.create", "roles.update", "roles.delete");

        for (var operation : clinicOperations(roleId)) {
            assertForbidden(operation.getValue().apply(clinic.token()), "FORBIDDEN");
        }
        assertThat(jdbc.queryForObject("select count(*) from role where id = ?::uuid", Integer.class, roleId)).isOne();
    }

    @Test
    void eachOperationRequiresItsOwnPermission() {
        for (String granted : List.of("roles.read", "roles.create", "roles.update", "roles.delete")) {
            Clinic clinic = api.clinic("Clinica " + granted);
            String roleId = createRole(clinic);
            revoke(clinic, List.of("roles.read", "roles.create", "roles.update", "roles.delete").stream()
                    .filter(code -> !code.equals(granted))
                    .toArray(String[]::new));

            for (var operation : clinicOperations(roleId)) {
                MvcTestResult result = operation.getValue().apply(clinic.token());
                if (operation.getKey().equals(granted)) {
                    assertThat(result.getResponse().getStatus()).as("%s with %s", operation.getKey(), granted)
                            .isBetween(200, 299);
                } else {
                    assertForbidden(result, "FORBIDDEN");
                }
            }
        }
    }

    @Test
    void anonymousCallerIsUnauthenticated() {
        assertThat(mvc.get().uri("/api/v1/roles")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/permissions")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/platform/permissions")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void superAdminIsForbiddenOnClinicOperations() {
        String platformToken = platformAdminToken();
        String roleId = createRole(api.clinic("Clinica Ajena al Super Admin"));

        for (var operation : clinicOperations(roleId)) {
            assertForbidden(operation.getValue().apply(platformToken), "FORBIDDEN");
        }
        assertThat(jdbc.queryForObject("select count(*) from role where id = ?::uuid", Integer.class, roleId)).isOne();
    }

    @Test
    void clinicStaffIsForbiddenOnPlatformOperations() {
        Clinic clinic = api.clinic("Clinica Sin Plataforma");

        assertForbidden(api.get("/api/v1/platform/permissions", clinic.token()), "FORBIDDEN");
    }

    @Test
    void superAdminListsTheWholeCatalog() {
        MvcTestResult result = api.get("/api/v1/platform/permissions", platformAdminToken());

        assertThat(result).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> catalog = api.bodyList(result);
        assertThat(catalog).extracting(permission -> permission.get("code")).containsExactly(
                "roles.read", "roles.create", "roles.update", "roles.delete", "platform.permissions.read");
        assertThat(catalog).extracting(permission -> permission.get("scope"))
                .containsExactly("TENANT", "TENANT", "TENANT", "TENANT", "PLATFORM");
        ContractSchema.identity().assertArrayResponse("/platform/permissions", "GET", 200, "application/json", catalog);
    }

    @Test
    void clinicListsOnlyAssignablePermissions() {
        MvcTestResult result = api.get("/api/v1/permissions", api.clinic("Clinica Catalogo").token());

        assertThat(result).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> catalog = api.bodyList(result);
        assertThat(catalog).extracting(permission -> permission.get("code"))
                .containsExactly("roles.read", "roles.create", "roles.update", "roles.delete");
        assertThat(catalog).allSatisfy(permission -> {
            assertThat(permission).containsEntry("scope", "TENANT");
            assertThat(permission.get("description")).asString().isNotBlank();
        });
        ContractSchema.identity().assertArrayResponse("/permissions", "GET", 200, "application/json", catalog);
    }

    @Test
    void forbiddenResponsesMatchTheContract() {
        Clinic clinic = api.clinic("Clinica Contrato 403");
        revoke(clinic, "roles.read");

        ContractSchema.identity().assertResponse(
                "/roles", "GET", 403, "application/problem+json", api.body(api.get("/api/v1/roles", clinic.token())));
    }

    private String createRole(Clinic clinic) {
        MvcTestResult created = api.post("/api/v1/roles", clinic.token(),
                Map.of("name", "Custom " + UUID.randomUUID(), "permissions", List.of("roles.read")));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private void revoke(Clinic clinic, String... codes) {
        for (String code : codes) {
            jdbc.update("delete from role_permission where tenant_id = ? and permission_code = ?", clinic.tenantId(), code);
        }
    }

    private static void assertForbidden(MvcTestResult result, String code) {
        assertThat(result)
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }

    /** A platform account that already changed its password (the bootstrap one belongs to BootstrapAdminIT). */
    private String platformAdminToken() {
        String email = IdentityApi.uniqueEmail("platform");
        String password = IdentityApi.randomPassword();
        jdbc.update("""
                insert into user_account (id, email, password_hash, display_name, platform_admin, must_change_password)
                values (?, ?, ?, 'Kuador', true, false)""",
                UUID.randomUUID(), email, passwordEncoder.encode(password));
        return api.token(email, password);
    }
}
