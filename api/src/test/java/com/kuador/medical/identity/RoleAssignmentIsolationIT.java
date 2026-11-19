package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Tenant isolation of seeded roles and branch role assignments (Constitution I): the owner of
 * clinic A cannot assign users, roles or branches of clinic B, and never sees or changes them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleAssignmentIsolationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private IdentityApi api;
    private StaffFixture staff;
    private Clinic clinicA;
    private Clinic clinicB;
    private UUID branchOfA;
    private UUID branchOfB;
    private UUID userOfA;
    private UUID userOfB;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staff = new StaffFixture(jdbc, passwordEncoder);
        clinicA = api.clinic("Clinica A");
        clinicB = api.clinic("Clinica B");
        branchOfA = staff.branch(clinicA.tenantId(), "Matriz A");
        branchOfB = staff.branch(clinicB.tenantId(), "Matriz B");
        userOfA = staff.staffUser(clinicA.tenantId());
        userOfB = staff.staffUser(clinicB.tenantId());
        assertThat(replace(clinicB, userOfB, Map.of("mode", "ALL_BRANCHES",
                "roleId", staff.role(clinicB.tenantId(), "RECEPTION")))).hasStatus(HttpStatus.OK);
    }

    @Test
    void seededRolesBelongOnlyToTheirClinic() {
        List<Map<String, Object>> rolesOfA = api.bodyList(api.get("/api/v1/roles", clinicA.token()));

        assertThat(rolesOfA).extracting(role -> UUID.fromString(role.get("id").toString()))
                .doesNotContainAnyElementsOf(jdbc.queryForList(
                        "select id from identity_role where tenant_id = ?", UUID.class, clinicB.tenantId()));
        assertThat(jdbc.queryForObject(
                "select count(*) from identity_role where tenant_id = ?", Integer.class, clinicB.tenantId()))
                .isEqualTo(rolesOfA.size());
    }

    @Test
    void userOfAnotherClinicIsNotFound() {
        MvcTestResult result = replace(clinicA, userOfB, Map.of("mode", "ALL_BRANCHES",
                "roleId", staff.role(clinicA.tenantId(), "DOCTOR")));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertUnchangedB();
    }

    @Test
    void roleOfAnotherClinicIsUnknown() {
        MvcTestResult result = replace(clinicA, userOfA, Map.of("mode", "PER_BRANCH", "assignments", List.of(
                Map.of("branchId", branchOfA, "roleId", staff.role(clinicB.tenantId(), "DOCTOR")))));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(result).get("errors"))
                .isEqualTo(List.of(Map.of("field", "assignments[0].roleId", "code", "UNKNOWN_ROLE")));
        assertThat(staff.assignments(userOfA)).isEmpty();
    }

    @Test
    void branchOfAnotherClinicIsUnknown() {
        MvcTestResult result = replace(clinicA, userOfA, Map.of("mode", "PER_BRANCH", "assignments", List.of(
                Map.of("branchId", branchOfB, "roleId", staff.role(clinicA.tenantId(), "DOCTOR")))));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(result).get("errors"))
                .isEqualTo(List.of(Map.of("field", "assignments[0].branchId", "code", "UNKNOWN_BRANCH")));
        assertThat(staff.assignments(userOfA)).isEmpty();
    }

    @Test
    void allBranchesOnlyUsesTheBranchesOfTheOwnClinic() {
        replace(clinicA, userOfA, Map.of("mode", "ALL_BRANCHES", "roleId", staff.role(clinicA.tenantId(), "DOCTOR")));

        assertThat(staff.assignments(userOfA)).singleElement().satisfies(assignment -> {
            assertThat(assignment.get("branch_id")).isEqualTo(branchOfA);
            assertThat(assignment.get("tenant_id")).isEqualTo(clinicA.tenantId());
        });
        assertUnchangedB();
    }

    @Test
    void assignedRoleOfAnotherClinicCannotBeDeletedNorSeen() {
        UUID receptionOfB = staff.role(clinicB.tenantId(), "RECEPTION");

        assertThat(api.delete("/api/v1/roles/" + receptionOfB, clinicA.token())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.get("/api/v1/roles/" + receptionOfB, clinicA.token())).hasStatus(HttpStatus.NOT_FOUND);
        assertUnchangedB();
    }

    private void assertUnchangedB() {
        assertThat(staff.assignments(userOfB)).singleElement().satisfies(assignment -> {
            assertThat(assignment.get("branch_id")).isEqualTo(branchOfB);
            assertThat(assignment.get("role_id")).isEqualTo(staff.role(clinicB.tenantId(), "RECEPTION"));
        });
    }

    private MvcTestResult replace(Clinic clinic, UUID userId, Map<String, Object> body) {
        return api.put("/api/v1/staff-users/" + userId + "/role-assignments", clinic.token(), body);
    }
}
