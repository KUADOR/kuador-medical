package com.kuador.medical.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/v1/tenants/signup (FR-009, FR-015, SC-008): the clinic, its owner account, the
 * membership and the system roles OWNER and ADMIN are created together, or not at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupIT {

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

    @Test
    void signupCreatesTheClinicWithOwnerAndAdminAssignedToItsCreator() {
        String email = IdentityApi.uniqueEmail("owner");
        String password = IdentityApi.randomPassword();

        MvcTestResult result = api.signUp(IdentityApi.signupRequest("Clinica Norte", email, password));

        assertThat(result).hasStatus(HttpStatus.CREATED).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        UUID tenantId = UUID.fromString(api.body(result).get("tenantId").toString());
        assertThat(jdbc.queryForList("select system_code from role where tenant_id = ?", String.class, tenantId))
                .containsExactlyInAnyOrder("OWNER", "ADMIN");
        assertThat(jdbc.queryForList("""
                select r.system_code from membership_role mr
                join role r on r.id = mr.role_id
                join tenant_membership m on m.id = mr.membership_id
                join user_account a on a.id = m.user_account_id
                where a.email = ? and m.tenant_id = ?""", String.class, email, tenantId))
                .containsExactlyInAnyOrder("OWNER", "ADMIN");
    }

    @Test
    void ownerLogsInToItsClinicWithRolesAndClinicPermissions() {
        String email = IdentityApi.uniqueEmail("owner");
        String password = IdentityApi.randomPassword();
        UUID tenantId = api.signUp("Clinica Sur", email, password);

        Map<String, Object> me = api.body(api.me(api.token(email, password)));

        assertThat(me).containsEntry("scope", "TENANT").containsEntry("mustChangePassword", false);
        assertThat(me.get("tenant")).isEqualTo(Map.of("id", tenantId.toString(), "name", "Clinica Sur"));
        assertThat((List<Object>) me.get("roles")).containsExactlyInAnyOrder("OWNER", "ADMIN");
        assertThat((List<Object>) me.get("permissions"))
                .containsExactlyInAnyOrder("roles.read", "roles.create", "roles.update", "roles.delete");
    }

    @Test
    void ownerAndAdminHaveEveryClinicPermissionAndNoPlatformPermission() {
        UUID tenantId = api.signUp("Clinica Este", IdentityApi.uniqueEmail("owner"), IdentityApi.randomPassword());

        List<String> codes = jdbc.queryForList(
                "select distinct permission_code from role_permission where tenant_id = ?", String.class, tenantId);

        assertThat(codes).containsExactlyInAnyOrder("roles.read", "roles.create", "roles.update", "roles.delete");
    }

    @Test
    void eachClinicOnlySeesItsOwnTenant() {
        String emailA = IdentityApi.uniqueEmail("owner-a");
        String emailB = IdentityApi.uniqueEmail("owner-b");
        String password = IdentityApi.randomPassword();
        UUID tenantA = api.signUp("Clinica A", emailA, password);
        UUID tenantB = api.signUp("Clinica B", emailB, password);

        Map<String, Object> meA = api.body(api.me(api.token(emailA, password)));
        Map<String, Object> meB = api.body(api.me(api.token(emailB, password)));

        assertThat(meA.get("tenant")).isEqualTo(Map.of("id", tenantA.toString(), "name", "Clinica A"));
        assertThat(meB.get("tenant")).isEqualTo(Map.of("id", tenantB.toString(), "name", "Clinica B"));
    }

    @Test
    void repeatedEmailRespondsConflictAndCreatesNothing() {
        String email = IdentityApi.uniqueEmail("owner");
        api.signUp("Clinica Original", email, IdentityApi.randomPassword());
        String clinicName = "Clinica Duplicada " + UUID.randomUUID();

        MvcTestResult result = api.signUp(
                IdentityApi.signupRequest(clinicName, email.toUpperCase(), IdentityApi.randomPassword()));

        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("EMAIL_TAKEN"));
        assertThat(jdbc.queryForObject("select count(*) from tenant where name = ?", Integer.class, clinicName))
                .isZero();
    }

    @Test
    void emailIsStoredNormalized() {
        String email = IdentityApi.uniqueEmail("Mixed.Case");
        String password = IdentityApi.randomPassword();

        api.signUp("Clinica Mayusculas", email.toUpperCase(), password);

        assertThat(jdbc.queryForObject("select count(*) from user_account where email = ?", Integer.class,
                email.toLowerCase())).isOne();
        assertThat(api.login(email.toLowerCase(), password)).hasStatus(HttpStatus.OK);
    }

    @Test
    void passwordIsStoredAsAnArgon2Hash() {
        String email = IdentityApi.uniqueEmail("owner");
        String password = IdentityApi.randomPassword();

        api.signUp("Clinica Hash", email, password);

        String hash = jdbc.queryForObject("select password_hash from user_account where email = ?", String.class, email);
        assertThat(hash).startsWith("{argon2}").doesNotContain(password);
    }

    @Test
    void invalidRequestRespondsValidation() {
        var tooShortPassword = IdentityApi.signupRequest("Clinica", IdentityApi.uniqueEmail("owner"), "short");
        var missingClinic = IdentityApi.signupRequest("", IdentityApi.uniqueEmail("owner"), IdentityApi.randomPassword());
        var badEmail = IdentityApi.signupRequest("Clinica", "not-an-email", IdentityApi.randomPassword());

        for (var request : List.of(tooShortPassword, missingClinic, badEmail)) {
            assertThat(api.signUp(request))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"));
        }
    }

    @Test
    void signupIsAuditedWithoutPersonalData() {
        String email = IdentityApi.uniqueEmail("owner");
        UUID tenantId = api.signUp("Clinica Auditada", email, IdentityApi.randomPassword());
        UUID accountId = jdbc.queryForObject("select id from user_account where email = ?", UUID.class, email);

        var events = jdbc.queryForList(
                "select actor_account_id, target_type, target_id from audit_event where tenant_id = ? and action = 'TENANT_SIGNED_UP'",
                tenantId);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.get("actor_account_id")).isEqualTo(accountId);
            assertThat(event.get("target_type")).isEqualTo("TENANT");
            assertThat(event.get("target_id")).isEqualTo(tenantId);
        });
    }

    @Test
    void responsesMatchTheContract() {
        String email = IdentityApi.uniqueEmail("owner");
        var request = IdentityApi.signupRequest("Clinica Contrato", email, IdentityApi.randomPassword());

        ContractSchema contract = ContractSchema.identity();
        contract.assertResponse("/tenants/signup", "POST", 201, "application/json", api.body(api.signUp(request)));
        contract.assertResponse("/tenants/signup", "POST", 409, "application/problem+json", api.body(api.signUp(request)));
        request.put("email", "");
        contract.assertResponse("/tenants/signup", "POST", 400, "application/problem+json", api.body(api.signUp(request)));
    }
}
