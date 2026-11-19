package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * CRUD of clinic roles (Historia 6, FR-006, FR-008, FR-011, FR-012, SC-006): rules, errors,
 * audit and responses checked against the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleManagementIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;
    private Clinic clinic;
    private final ContractSchema contract = ContractSchema.userRegistration();

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        clinic = api.clinic("Clinica Roles");
    }

    @Test
    void createdRoleIsListedAndReadable() {
        MvcTestResult created = create(Map.of(
                "name", "Reception", "description", "Front desk", "permissions", List.of("roles.create", "roles.read")));

        assertThat(created).hasStatus(HttpStatus.CREATED).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        Map<String, Object> role = api.body(created);
        assertThat(role)
                .containsEntry("name", "Reception")
                .containsEntry("description", "Front desk")
                .containsEntry("systemRole", false)
                .containsEntry("systemKey", null)
                .containsEntry("clinical", false)
                .containsEntry("version", 0)
                .containsEntry("permissions", List.of("roles.read", "roles.create"));
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/roles/" + role.get("id"));
        contract.assertResponse("/roles", "POST", 201, "application/json", role);

        List<Map<String, Object>> roles = api.bodyList(api.get("/api/v1/roles", clinic.token()));
        assertThat(roles).extracting(found -> found.get("name"))
                .startsWith("OWNER", "ADMIN")
                .contains("Reception", "Recepción")
                .hasSize(12);
        contract.assertArrayResponse("/roles", "GET", 200, "application/json", roles);

        MvcTestResult read = api.get("/api/v1/roles/" + role.get("id"), clinic.token());
        assertThat(read).hasStatus(HttpStatus.OK);
        assertThat(api.body(read)).isEqualTo(role);
        contract.assertResponse("/roles/{id}", "GET", 200, "application/json", api.body(read));
    }

    @Test
    void systemRolesAreListedWithEveryClinicPermission() {
        List<Map<String, Object>> roles = systemRoles();

        assertThat(roles).extracting(role -> role.get("systemKey")).containsExactly("OWNER", "ADMIN");
        assertThat(roles).allSatisfy(role -> {
            assertThat(role).containsEntry("systemRole", true);
            assertThat(role.get("permissions"))
                    .isEqualTo(Permission.ofScope(PermissionScope.TENANT).stream().map(Permission::code).toList());
        });
    }

    @Test
    void updatedRoleReflectsTheChangesAndIncrementsItsVersion() {
        String id = createdId("Reception", List.of("roles.read"));

        MvcTestResult updated = update(id, "Front desk", "Phone and desk", List.of("roles.update"), 0);

        assertThat(updated).hasStatus(HttpStatus.OK);
        Map<String, Object> role = api.body(updated);
        assertThat(role)
                .containsEntry("name", "Front desk")
                .containsEntry("description", "Phone and desk")
                .containsEntry("permissions", List.of("roles.update"))
                .containsEntry("version", 1);
        contract.assertResponse("/roles/{id}", "PUT", 200, "application/json", role);
        assertThat(api.body(api.get("/api/v1/roles/" + id, clinic.token()))).isEqualTo(role);
    }

    @Test
    void changingOnlyThePermissionsAlsoIncrementsTheVersion() {
        String id = createdId("Reception", List.of("roles.read"));

        assertThat(api.body(update(id, "Reception", null, List.of("roles.read", "roles.delete"), 0)))
                .containsEntry("version", 1)
                .containsEntry("permissions", List.of("roles.read", "roles.delete"));
        assertThat(update(id, "Reception", null, List.of(), 0)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deletedRoleNoLongerExists() {
        String id = createdId("Reception", List.of("roles.read"));

        assertThat(api.delete("/api/v1/roles/" + id, clinic.token())).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(api.get("/api/v1/roles/" + id, clinic.token())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.bodyList(api.get("/api/v1/roles", clinic.token())))
                .extracting(role -> role.get("id")).doesNotContain(id);
        assertThat(jdbc.queryForObject("select count(*) from identity_role_permission where role_id = ?::uuid", Integer.class, id))
                .isZero();
        assertThat(api.delete("/api/v1/roles/" + id, clinic.token())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void nameIsUniquePerClinicIgnoringCase() {
        createdId("Reception", List.of());

        MvcTestResult duplicate = create(Map.of("name", " RECEPTION ", "permissions", List.of()));

        assertConflict(duplicate, "ROLE_NAME_TAKEN");
        contract.assertResponse("/roles", "POST", 409, "application/problem+json", api.body(duplicate));
        assertConflict(create(Map.of("name", "owner", "permissions", List.of())), "ROLE_NAME_TAKEN");
    }

    @Test
    void renamingToANameInUseIsAConflict() {
        createdId("Reception", List.of());
        String id = createdId("Nursing", List.of());

        assertConflict(update(id, "reception", null, List.of(), 0), "ROLE_NAME_TAKEN");
        assertThat(update(id, "NURSING", null, List.of(), 0)).hasStatus(HttpStatus.OK);
    }

    @Test
    void permissionOutsideTheClinicCatalogIsRejected() {
        for (String code : List.of("billing.unknown", "platform.permissions.read")) {
            MvcTestResult result = create(Map.of("name", "Invalid " + code, "permissions", List.of("roles.read", code)));

            assertThat(result)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo("VALIDATION"))
                    .hasPathSatisfying("$.errors[0].field", value -> assertThat(value).asString().isEqualTo("permissions[1]"))
                    .hasPathSatisfying("$.errors[0].code",
                            value -> assertThat(value).asString().isEqualTo("UNKNOWN_PERMISSION"));
            contract.assertResponse("/roles", "POST", 400, "application/problem+json", api.body(result));
        }
        String id = createdId("Reception", List.of("roles.read"));
        assertThat(update(id, "Reception", null, List.of("platform.permissions.read"), 0))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForList("select permission_code from identity_role_permission where role_id = ?::uuid", String.class, id))
                .containsExactly("roles.read");
    }

    @Test
    void invalidRequestRespondsValidation() {
        Map<String, Object> withoutPermissions = new HashMap<>(Map.of("name", "Reception"));
        List<Map<String, Object>> requests = List.of(
                Map.of("name", "", "permissions", List.of()),
                Map.of("name", "x".repeat(81), "permissions", List.of()),
                Map.of("name", "Reception", "description", "x".repeat(501), "permissions", List.of()),
                withoutPermissions);

        for (Map<String, Object> request : requests) {
            assertThat(create(request))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo("VALIDATION"));
        }
        String id = createdId("Reception", List.of());
        assertThat(api.put("/api/v1/roles/" + id, clinic.token(), Map.of("name", "Reception", "permissions", List.of())))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void protectedRolesCannotBeDeletedAndOwnerCannotBeUpdated() {
        for (Map<String, Object> systemRole : systemRoles()) {
            String id = systemRole.get("id").toString();

            MvcTestResult deleted = api.delete("/api/v1/roles/" + id, clinic.token());
            assertConflict(deleted, "SYSTEM_ROLE_IMMUTABLE");
            contract.assertResponse("/roles/{id}", "DELETE", 409, "application/problem+json", api.body(deleted));
        }
        String owner = systemRoles().getFirst().get("id").toString();
        MvcTestResult updated = update(owner, "Renamed", null, List.of("roles.read"), 0);
        assertConflict(updated, "SYSTEM_ROLE_IMMUTABLE");
        contract.assertResponse("/roles/{id}", "PUT", 409, "application/problem+json", api.body(updated));

        assertThat(systemRoles()).extracting(role -> role.get("name")).containsExactly("OWNER", "ADMIN");
        int clinicPermissions = Permission.ofScope(PermissionScope.TENANT).size();
        assertThat(systemRoles())
                .allSatisfy(role -> assertThat(((List<?>) role.get("permissions"))).hasSize(clinicPermissions));
    }

    @Test
    void ownerEditsTheAdminRole() {
        String admin = systemRoles().get(1).get("id").toString();

        MvcTestResult updated = update(admin, "ADMIN", "Gestión diaria", List.of("roles.read", "identity.users.read"), 0);

        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(api.body(updated))
                .containsEntry("systemKey", "ADMIN")
                .containsEntry("systemRole", true)
                .containsEntry("permissions", List.of("roles.read", "identity.users.read"))
                .containsEntry("version", 1);
        contract.assertResponse("/roles/{id}", "PUT", 200, "application/json", api.body(updated));
    }

    @Test
    void clinicalFlagIsStoredAndUpdated() {
        MvcTestResult created = create(Map.of("name", "Fisioterapia", "clinical", true, "permissions", List.of()));

        assertThat(api.body(created)).containsEntry("clinical", true);
        String id = api.body(created).get("id").toString();
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Fisioterapia");
        request.put("clinical", false);
        request.put("permissions", List.of());
        request.put("version", 0);
        assertThat(api.body(api.put("/api/v1/roles/" + id, clinic.token(), request))).containsEntry("clinical", false);
    }

    @Test
    void clientMayChooseTheIdOfANewRoleOnce() {
        String id = UUID.randomUUID().toString();

        MvcTestResult created = create(Map.of("id", id, "name", "Laboratorio", "permissions", List.of()));
        MvcTestResult again = create(Map.of("id", id, "name", "Imagenología", "permissions", List.of()));

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(api.body(created)).containsEntry("id", id);
        assertConflict(again, "RESOURCE_ID_TAKEN");
    }

    @Test
    void staleVersionIsAConflict() {
        String id = createdId("Reception", List.of("roles.read"));
        assertThat(update(id, "Front desk", null, List.of("roles.read"), 0)).hasStatus(HttpStatus.OK);

        MvcTestResult stale = update(id, "Other name", null, List.of(), 0);

        assertConflict(stale, "VERSION_CONFLICT");
        assertThat(api.body(api.get("/api/v1/roles/" + id, clinic.token()))).containsEntry("name", "Front desk");
    }

    @Test
    void unknownRoleIsNotFound() {
        String missing = UUID.randomUUID().toString();

        MvcTestResult read = api.get("/api/v1/roles/" + missing, clinic.token());

        assertThat(read).hasStatus(HttpStatus.NOT_FOUND);
        contract.assertResponse("/roles/{id}", "GET", 404, "application/problem+json", api.body(read));
        assertThat(update(missing, "Reception", null, List.of(), 0)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.delete("/api/v1/roles/" + missing, clinic.token())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void everyRoleOperationIsAuditedWithoutPersonalData() {
        UUID ownerId = jdbc.queryForObject(
                "select user_account_id from tenant_membership where tenant_id = ?", UUID.class, clinic.tenantId());
        String id = createdId("Reception", List.of("roles.read"));
        update(id, "Front desk", null, List.of(), 0);
        api.delete("/api/v1/roles/" + id, clinic.token());

        var events = jdbc.queryForList("""
                select action, actor_account_id, target_type, target_id from audit_event
                where tenant_id = ? and action in ('ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED') order by occurred_at""",
                clinic.tenantId());

        assertThat(events).extracting(event -> event.get("action"))
                .containsExactly("ROLE_CREATED", "ROLE_UPDATED", "ROLE_DELETED");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.get("actor_account_id")).isEqualTo(ownerId);
            assertThat(event.get("target_type")).isEqualTo("ROLE");
            assertThat(event.get("target_id")).isEqualTo(UUID.fromString(id));
        });
    }

    @Test
    void rejectedOperationsAreNotAudited() {
        createdId("Reception", List.of());
        create(Map.of("name", "Reception", "permissions", List.of()));
        String ownerRole = systemRoles().getFirst().get("id").toString();
        api.delete("/api/v1/roles/" + ownerRole, clinic.token());

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where tenant_id = ? and action in ('ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED')",
                Integer.class, clinic.tenantId())).isOne();
    }

    private MvcTestResult create(Map<String, Object> request) {
        return api.post("/api/v1/roles", clinic.token(), request);
    }

    private String createdId(String name, List<String> permissions) {
        MvcTestResult created = create(Map.of("name", name, "permissions", permissions));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private MvcTestResult update(String id, String name, String description, List<String> permissions, long version) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("description", description);
        request.put("permissions", permissions);
        request.put("version", version);
        return api.put("/api/v1/roles/" + id, clinic.token(), request);
    }

    private List<Map<String, Object>> systemRoles() {
        return api.bodyList(api.get("/api/v1/roles", clinic.token())).stream()
                .filter(role -> Boolean.TRUE.equals(role.get("systemRole")))
                .toList();
    }

    private static void assertConflict(MvcTestResult result, String code) {
        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }
}
