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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tenant isolation of roles (FR-010, SC-004, Constitution I): an owner of clinic A neither reads
 * nor writes the roles of clinic B, and roles of another clinic respond 404 as if they did not exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleIsolationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;
    private Clinic clinicA;
    private Clinic clinicB;
    private String roleOfB;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        clinicA = api.clinic("Clinica A");
        clinicB = api.clinic("Clinica B");
        MvcTestResult created = api.post("/api/v1/roles", clinicB.token(),
                Map.of("name", "Reception", "permissions", List.of("roles.read")));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        roleOfB = api.body(created).get("id").toString();
    }

    @Test
    void listOnlyContainsTheRolesOfTheOwnClinic() {
        List<Map<String, Object>> rolesOfA = api.bodyList(api.get("/api/v1/roles", clinicA.token()));

        assertThat(rolesOfA).extracting(role -> role.get("name")).containsExactly("OWNER", "ADMIN");
        assertThat(rolesOfA).extracting(role -> role.get("id")).doesNotContain(roleOfB);
        assertThat(rolesOfA).extracting(role -> role.get("id")).doesNotContainAnyElementsOf(systemRoleIdsOf(clinicB));
    }

    @Test
    void roleOfAnotherClinicIsNotFound() {
        assertNotFound(api.get("/api/v1/roles/" + roleOfB, clinicA.token()));
        for (String systemRoleOfB : systemRoleIdsOf(clinicB)) {
            assertNotFound(api.get("/api/v1/roles/" + systemRoleOfB, clinicA.token()));
        }
    }

    @Test
    void roleOfAnotherClinicCannotBeUpdated() {
        assertNotFound(api.put("/api/v1/roles/" + roleOfB, clinicA.token(),
                Map.of("name", "Hijacked", "permissions", List.of("roles.read", "roles.delete"), "version", 0)));

        Map<String, Object> unchanged = api.body(api.get("/api/v1/roles/" + roleOfB, clinicB.token()));
        assertThat(unchanged).containsEntry("name", "Reception").containsEntry("version", 0);
        assertThat((List<Object>) unchanged.get("permissions")).containsExactly("roles.read");
    }

    @Test
    void roleOfAnotherClinicCannotBeDeleted() {
        assertNotFound(api.delete("/api/v1/roles/" + roleOfB, clinicA.token()));
        for (String systemRoleOfB : systemRoleIdsOf(clinicB)) {
            assertNotFound(api.delete("/api/v1/roles/" + systemRoleOfB, clinicA.token()));
        }

        assertThat(api.get("/api/v1/roles/" + roleOfB, clinicB.token())).hasStatus(HttpStatus.OK);
        assertThat(roleCount(clinicB.tenantId())).isEqualTo(3);
    }

    @Test
    void roleCreatedByAClinicNeverLandsInAnotherClinic() {
        Map<String, Object> request = Map.of(
                "name", "Nursing", "permissions", List.of("roles.read"), "tenantId", clinicB.tenantId().toString());

        MvcTestResult created = api.post("/api/v1/roles", clinicA.token(), request);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID id = UUID.fromString(api.body(created).get("id").toString());
        assertThat(jdbc.queryForObject("select tenant_id from role where id = ?", UUID.class, id))
                .isEqualTo(clinicA.tenantId());
        assertThat(jdbc.queryForList("select tenant_id from role_permission where role_id = ?", UUID.class, id))
                .containsOnly(clinicA.tenantId());
        assertThat(roleCount(clinicB.tenantId())).isEqualTo(3);
    }

    @Test
    void sameNameIsValidInAnotherClinic() {
        assertThat(api.post("/api/v1/roles", clinicA.token(), Map.of("name", "reception", "permissions", List.of())))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void auditEventsBelongToTheClinicOfTheCaller() {
        assertThat(jdbc.queryForObject(
                "select tenant_id from audit_event where action = 'ROLE_CREATED' and target_id = ?::uuid",
                UUID.class, roleOfB)).isEqualTo(clinicB.tenantId());
    }

    private List<String> systemRoleIdsOf(Clinic clinic) {
        return jdbc.queryForList(
                "select id::text from role where tenant_id = ? and system_code is not null", String.class, clinic.tenantId());
    }

    private int roleCount(UUID tenantId) {
        return jdbc.queryForObject("select count(*) from role where tenant_id = ?", Integer.class, tenantId);
    }

    private static void assertNotFound(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("NOT_FOUND"));
    }
}
