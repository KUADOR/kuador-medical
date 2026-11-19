package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
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
 * {@code PUT /staff-users/{id}/role-assignments} (ADR-013, rules 1 and 2 of the plan of MEDK-12)
 * called by the owner of the clinic. Rules for non-owner callers are in
 * {@code identity.role.AssignmentRulesIT} and {@code EscalationGuardTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleAssignmentIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ContractSchema contract = ContractSchema.userRegistration();
    private IdentityApi api;
    private StaffFixture staff;
    private Clinic clinic;
    private UUID main;
    private UUID north;
    private UUID user;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staff = new StaffFixture(jdbc, passwordEncoder);
        clinic = api.clinic("Clinica Sucursales");
        main = staff.branch(clinic.tenantId(), "Matriz");
        north = staff.branch(clinic.tenantId(), "Norte");
        user = staff.staffUser(clinic.tenantId());
    }

    @Test
    void signupSeedsTheRolesAndMakesTheOwnerClinicWide() {
        List<Map<String, Object>> roles = api.bodyList(api.get("/api/v1/roles", clinic.token()));

        assertThat(roles).extracting(role -> role.get("name")).containsExactly(
                "OWNER", "ADMIN", "Bodeguero", "Cajero", "Cobranzas", "Enfermería", "Farmacéutico",
                "Médico", "Odontólogo", "Paciente", "Recepción");
        assertThat(roles).filteredOn(role -> Boolean.TRUE.equals(role.get("clinical")))
                .extracting(role -> role.get("name"))
                .containsExactlyInAnyOrder("Médico", "Odontólogo", "Enfermería");
        assertThat(roles).filteredOn(role -> "Recepción".equals(role.get("name"))).singleElement()
                .satisfies(role -> assertThat(role.get("permissions")).isEqualTo(List.of(
                        "patients.read", "patients.register", "patients.edit", "patients.guardians.manage")));
        contract.assertArrayResponse("/roles", "GET", 200, "application/json", roles);

        UUID owner = staff.ownerOf(clinic.tenantId());
        assertThat(staff.assignments(owner)).singleElement().satisfies(assignment -> {
            assertThat(assignment.get("branch_id")).isNull();
            assertThat(assignment.get("role_id")).isEqualTo(staff.role(clinic.tenantId(), "OWNER"));
        });
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where tenant_id = ? and action = 'ROLES_SEEDED'",
                Integer.class, clinic.tenantId())).isOne();
    }

    @Test
    void allBranchesAssignsTheRoleInEveryExistingBranchOnly() {
        UUID reception = staff.role(clinic.tenantId(), "RECEPTION");

        MvcTestResult result = replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", reception));

        assertThat(result).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> body = api.bodyList(result);
        assertThat(body).extracting(assignment -> branchName(assignment)).containsExactly("Matriz", "Norte");
        assertThat(body).extracting(assignment -> roleName(assignment)).containsOnly("Recepción");
        contract.assertArrayResponse("/staff-users/{id}/role-assignments", "PUT", 200, "application/json", body);

        staff.branch(clinic.tenantId(), "Sur");
        assertThat(staff.assignments(user)).hasSize(2)
                .extracting(assignment -> assignment.get("branch_id")).containsExactlyInAnyOrder(main, north);
    }

    @Test
    void perBranchAssignsOneRolePerBranchAndReplacesThePreviousOnes() {
        UUID reception = staff.role(clinic.tenantId(), "RECEPTION");
        UUID doctor = staff.role(clinic.tenantId(), "DOCTOR");
        replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", reception));

        MvcTestResult result = replace(user, Map.of("mode", "PER_BRANCH", "assignments", List.of(
                Map.of("branchId", north, "roleId", doctor))));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(api.bodyList(result)).singleElement().satisfies(assignment -> {
            assertThat(branchName(assignment)).isEqualTo("Norte");
            assertThat(roleName(assignment)).isEqualTo("Médico");
        });
        assertThat(staff.assignments(user)).singleElement()
                .satisfies(assignment -> assertThat(assignment.get("role_id")).isEqualTo(doctor));
    }

    @Test
    void ownerIsClinicWideAndAnotherOwnerCanBeRemoved() {
        UUID ownerRole = staff.role(clinic.tenantId(), "OWNER");

        MvcTestResult granted = replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", ownerRole));

        assertThat(granted).hasStatus(HttpStatus.OK);
        assertThat(api.bodyList(granted)).singleElement().satisfies(assignment -> {
            assertThat(assignment.get("branch")).isNull();
            assertThat(roleName(assignment)).isEqualTo("OWNER");
        });
        contract.assertArrayResponse(
                "/staff-users/{id}/role-assignments", "PUT", 200, "application/json", api.bodyList(granted));

        UUID admin = staff.role(clinic.tenantId(), "ADMIN");
        assertThat(replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", admin))).hasStatus(HttpStatus.OK);
        assertThat(staff.assignments(user)).hasSize(2)
                .allSatisfy(assignment -> assertThat(assignment.get("role_id")).isEqualTo(admin));
    }

    @Test
    void nobodyChangesTheirOwnAssignments() {
        UUID owner = staff.ownerOf(clinic.tenantId());
        UUID admin = staff.role(clinic.tenantId(), "ADMIN");

        MvcTestResult result = replace(owner, Map.of("mode", "ALL_BRANCHES", "roleId", admin));

        assertProblem(result, HttpStatus.CONFLICT, "SELF_MODIFICATION_FORBIDDEN");
        contract.assertResponse(
                "/staff-users/{id}/role-assignments", "PUT", 409, "application/problem+json", api.body(result));
        assertThat(staff.assignments(owner)).singleElement()
                .satisfies(assignment -> assertThat(assignment.get("branch_id")).isNull());
    }

    @Test
    void invalidAssignmentsRespondValidationWithTheirField() {
        UUID reception = staff.role(clinic.tenantId(), "RECEPTION");
        UUID ownerRole = staff.role(clinic.tenantId(), "OWNER");

        MvcTestResult result = replace(user, Map.of("mode", "PER_BRANCH", "assignments", List.of(
                Map.of("branchId", main, "roleId", reception),
                Map.of("branchId", UUID.randomUUID(), "roleId", reception),
                Map.of("branchId", main, "roleId", UUID.randomUUID()),
                Map.of("branchId", north, "roleId", ownerRole))));

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION");
        assertThat(errors(result)).containsExactly(
                Map.of("field", "assignments[1].branchId", "code", "UNKNOWN_BRANCH"),
                Map.of("field", "assignments[2].branchId", "code", "DUPLICATE"),
                Map.of("field", "assignments[2].roleId", "code", "UNKNOWN_ROLE"),
                Map.of("field", "assignments[3].roleId", "code", "OWNER_REQUIRES_ALL_BRANCHES"));
        contract.assertResponse(
                "/staff-users/{id}/role-assignments", "PUT", 400, "application/problem+json", api.body(result));

        assertThat(errors(replace(user, Map.of("mode", "ALL_BRANCHES"))))
                .containsExactly(Map.of("field", "roleId", "code", "REQUIRED"));
        assertThat(errors(replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", UUID.randomUUID()))))
                .containsExactly(Map.of("field", "roleId", "code", "UNKNOWN_ROLE"));
        assertThat(errors(replace(user, Map.of("mode", "PER_BRANCH", "assignments", List.of()))))
                .containsExactly(Map.of("field", "assignments", "code", "REQUIRED"));
        assertThat(replace(user, Map.of("mode", "SOMEWHERE", "roleId", reception))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(staff.assignments(user)).isEmpty();
    }

    @Test
    void unknownUserIsNotFound() {
        UUID reception = staff.role(clinic.tenantId(), "RECEPTION");

        MvcTestResult result = replace(UUID.randomUUID(), Map.of("mode", "ALL_BRANCHES", "roleId", reception));

        assertProblem(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void assignedRoleCannotBeDeleted() {
        UUID doctor = staff.role(clinic.tenantId(), "DOCTOR");
        replace(user, Map.of("mode", "PER_BRANCH", "assignments", List.of(Map.of("branchId", main, "roleId", doctor))));

        assertProblem(api.delete("/api/v1/roles/" + doctor, clinic.token()), HttpStatus.CONFLICT, "ROLE_IN_USE");

        replace(user, Map.of("mode", "PER_BRANCH", "assignments", List.of(
                Map.of("branchId", main, "roleId", staff.role(clinic.tenantId(), "NURSE")))));
        assertThat(api.delete("/api/v1/roles/" + doctor, clinic.token())).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void everyReplacementIsAuditedWithoutPersonalDataAndRejectedOnesAreNot() {
        UUID owner = staff.ownerOf(clinic.tenantId());
        UUID reception = staff.role(clinic.tenantId(), "RECEPTION");
        replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", reception));
        replace(owner, Map.of("mode", "ALL_BRANCHES", "roleId", reception));
        replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", UUID.randomUUID()));

        var events = jdbc.queryForList("""
                select actor_account_id, target_type, target_id from audit_event
                where tenant_id = ? and action = 'ROLE_ASSIGNMENTS_REPLACED'""", clinic.tenantId());

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.get("actor_account_id")).isEqualTo(owner);
            assertThat(event.get("target_type")).isEqualTo("ACCOUNT");
            assertThat(event.get("target_id")).isEqualTo(user);
        });
    }

    @Test
    void requiresTheAssignmentPermission() {
        jdbc.update("delete from identity_role_permission where tenant_id = ? and permission_code = 'identity.assignments.manage'",
                clinic.tenantId());

        MvcTestResult result = replace(user, Map.of("mode", "ALL_BRANCHES", "roleId", staff.role(clinic.tenantId(), "DOCTOR")));

        assertProblem(result, HttpStatus.FORBIDDEN, "FORBIDDEN");
        contract.assertResponse(
                "/staff-users/{id}/role-assignments", "PUT", 403, "application/problem+json", api.body(result));
        assertThat(mvc.put().uri("/api/v1/staff-users/" + user + "/role-assignments")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult replace(UUID userId, Map<String, Object> body) {
        return api.put("/api/v1/staff-users/" + userId + "/role-assignments", clinic.token(), body);
    }

    @SuppressWarnings("unchecked")
    private static String branchName(Map<String, Object> assignment) {
        return ((Map<String, Object>) assignment.get("branch")).get("name").toString();
    }

    @SuppressWarnings("unchecked")
    private static String roleName(Map<String, Object> assignment) {
        return ((Map<String, Object>) assignment.get("role")).get("name").toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errors(MvcTestResult result) {
        return (List<Map<String, Object>>) api.body(result).get("errors");
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result)
                .hasStatus(status)
                .bodyJson()
                .hasPathSatisfying("$.code", found -> assertThat(found).asString().isEqualTo(code));
    }
}
