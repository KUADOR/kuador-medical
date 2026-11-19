package com.kuador.medical.identity;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Branch-scoped sessions (ADR-013, spec story 6): the token carries the clinic, the active branch
 * and the permissions of that assignment; switching branch issues new tokens; /me reports the
 * session and the modules enabled by the plan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BranchSessionIT {

    private static final List<String> RECEPTION_PERMISSIONS =
            List.of("patients.read", "patients.register", "patients.edit", "patients.guardians.manage");

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
    private UUID tenantId;
    private UUID main;
    private UUID north;
    private UUID unassigned;
    private String email;
    private String password;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staff = new StaffFixture(jdbc, passwordEncoder);
        tenantId = api.signUp("Clinica Sucursales", IdentityApi.uniqueEmail("owner"), IdentityApi.randomPassword());
        main = staff.branch(tenantId, "Matriz");
        north = staff.branch(tenantId, "Norte");
        unassigned = staff.branch(tenantId, "Sur");
        email = IdentityApi.uniqueEmail("staff");
        password = IdentityApi.randomPassword();
        UUID user = staff.staffUser(tenantId, email, password);
        staff.assign(tenantId, user, main, staff.role(tenantId, "ADMIN"));
        staff.assign(tenantId, user, north, staff.role(tenantId, "RECEPTION"));
    }

    @Test
    void severalAssignedBranchesRequireAChoice() {
        Map<String, Object> body = api.body(api.login(email, password));

        assertThat(body).containsEntry("status", "BRANCH_SELECTION_REQUIRED")
                .containsEntry("clinic", Map.of("id", tenantId.toString(), "name", "Clinica Sucursales"));
        assertThat(branchIds(body, "branches")).containsExactlyInAnyOrder(main.toString(), north.toString());
        ContractSchema.userRegistration().assertResponse("/auth/login", "POST", 200, "application/json", body);
    }

    @Test
    void theTokenCarriesTheBranchAndThePermissionsOfThatAssignment() {
        Map<String, Object> session = select(north);

        assertThat(session).containsEntry("branch", Map.of("id", north.toString(), "name", "Norte"));
        Map<String, Object> claims = api.claims(session.get("accessToken").toString());
        assertThat(claims).containsEntry("tid", tenantId.toString()).containsEntry("bid", north.toString());
        assertThat(claims.get("permissions")).isEqualTo(RECEPTION_PERMISSIONS);
        ContractSchema.userRegistration().assertResponse("/auth/login/branch", "POST", 200, "application/json", session);
    }

    @Test
    void meReportsTheActiveBranchItsPermissionsAndThePlanModules() {
        String token = select(north).get("accessToken").toString();

        Map<String, Object> me = api.body(api.me(token));

        assertThat(me).containsEntry("kind", "STAFF").containsEntry("scope", "TENANT")
                .containsEntry("activeBranch", Map.of("id", north.toString(), "name", "Norte"))
                .containsEntry("permissions", RECEPTION_PERMISSIONS)
                .containsEntry("roles", List.of("Recepción"))
                .containsEntry("enabledModules", List.of("identity"));
        assertThat(branchIds(me, "availableBranches")).containsExactlyInAnyOrder(main.toString(), north.toString());
        ContractSchema.userRegistration().assertResponse("/me", "GET", 200, "application/json", me);
    }

    @Test
    void permissionsOfAnotherBranchDoNotApply() {
        String receptionToken = select(north).get("accessToken").toString();

        assertThat(api.get("/api/v1/roles", receptionToken)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void switchingBranchIssuesTokensWithThePermissionsOfTheNewAssignment() {
        Map<String, Object> reception = select(north);

        MvcTestResult switched = api.switchBranch(
                reception.get("accessToken").toString(), main, reception.get("refreshToken").toString());

        assertThat(switched).hasStatus(HttpStatus.OK);
        Map<String, Object> admin = api.body(switched);
        assertThat(admin).containsEntry("branch", Map.of("id", main.toString(), "name", "Matriz"));
        String adminToken = admin.get("accessToken").toString();
        assertThat(api.claims(adminToken)).containsEntry("bid", main.toString());
        assertThat(api.get("/api/v1/roles", adminToken)).hasStatus(HttpStatus.OK);
        assertThat(api.refresh(reception.get("refreshToken").toString()))
                .as("the previous refresh token is revoked")
                .hasStatus(HttpStatus.UNAUTHORIZED);
        ContractSchema.userRegistration().assertResponse("/auth/switch-branch", "POST", 200, "application/json", admin);
    }

    @Test
    void switchingToABranchWithoutAssignmentIsRejected() {
        String token = select(north).get("accessToken").toString();

        for (UUID branch : List.of(unassigned, UUID.randomUUID())) {
            assertThat(api.switchBranch(token, branch, null))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("BRANCH_NOT_ASSIGNED"));
        }
    }

    @Test
    void choosingABranchWithoutAssignmentIsRejected() {
        String preAuth = api.body(api.login(email, password)).get("preAuthToken").toString();

        assertThat(api.selectBranch(preAuth, unassigned))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("BRANCH_NOT_ASSIGNED"));
    }

    @Test
    void aBranchOfAnotherClinicIsNeverAssigned() {
        UUID otherTenant = api.signUp("Clinica Vecina", IdentityApi.uniqueEmail("owner"), IdentityApi.randomPassword());
        UUID foreignBranch = staff.branch(otherTenant, "Ajena");
        String token = select(north).get("accessToken").toString();

        assertThat(api.switchBranch(token, foreignBranch, null)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aSingleAssignedBranchIsEnteredDirectly() {
        String singleEmail = IdentityApi.uniqueEmail("single");
        String singlePassword = IdentityApi.randomPassword();
        UUID user = staff.staffUser(tenantId, singleEmail, singlePassword);
        staff.assign(tenantId, user, north, staff.role(tenantId, "RECEPTION"));

        Map<String, Object> body = api.body(api.login(singleEmail, singlePassword));

        assertThat(body).containsEntry("status", "AUTHENTICATED")
                .containsEntry("branch", Map.of("id", north.toString(), "name", "Norte"));
    }

    @Test
    void theOwnerChoosesAmongEveryBranchOfTheClinic() {
        String ownerEmail = IdentityApi.uniqueEmail("owner");
        String ownerPassword = IdentityApi.randomPassword();
        UUID tenant = api.signUp("Clinica Propia", ownerEmail, ownerPassword);
        UUID first = staff.branch(tenant, "Primera");
        UUID second = staff.branch(tenant, "Segunda");

        Map<String, Object> body = api.body(api.login(ownerEmail, ownerPassword));

        assertThat(body).containsEntry("status", "BRANCH_SELECTION_REQUIRED");
        assertThat(branchIds(body, "branches")).containsExactlyInAnyOrder(first.toString(), second.toString());
    }

    private Map<String, Object> select(UUID branch) {
        String preAuth = api.body(api.login(email, password)).get("preAuthToken").toString();
        MvcTestResult selected = api.selectBranch(preAuth, branch);
        assertThat(selected).hasStatus(HttpStatus.OK);
        return api.body(selected);
    }

    @SuppressWarnings("unchecked")
    private static List<String> branchIds(Map<String, Object> body, String field) {
        return ((List<Map<String, Object>>) body.get(field)).stream().map(branch -> branch.get("id").toString()).toList();
    }
}
