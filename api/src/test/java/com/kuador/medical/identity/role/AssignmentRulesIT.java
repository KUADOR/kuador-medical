package com.kuador.medical.identity.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionChecker;
import com.kuador.medical.identity.StaffFixture;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Anti-escalation with a caller that is not OWNER: ADMIN in the main branch and doctor in the
 * north branch. Until the token carries the active branch (MEDK-15) such a caller cannot reach
 * the API, so the services and {@link PermissionChecker} are called with the caller bound as in
 * a request of its main branch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssignmentRulesIT {

    private static final String REQUEST_ACCOUNT = "com.kuador.medical.identity.auth.RequestAccounts.account";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AssignmentService assignments;

    @Autowired
    private RoleService roles;

    @Autowired
    private PermissionChecker permissionChecker;

    private StaffFixture staff;
    private UUID tenantId;
    private UUID main;
    private UUID north;
    private UUID admin;
    private UUID target;
    private AuthenticatedAccount adminInMain;

    @BeforeEach
    void setUp() {
        staff = new StaffFixture(jdbc, passwordEncoder);
        tenantId = new IdentityApi(mvc, jsonMapper).clinic("Clinica Reglas").tenantId();
        main = staff.branch(tenantId, "Matriz");
        north = staff.branch(tenantId, "Norte");
        admin = staff.staffUser(tenantId);
        target = staff.staffUser(tenantId);
        staff.assign(tenantId, admin, main, staff.role(tenantId, "ADMIN"));
        staff.assign(tenantId, admin, north, staff.role(tenantId, "DOCTOR"));
        List<String> adminPermissions = Permission.ofScope(com.kuador.medical.identity.PermissionScope.TENANT)
                .stream().map(Permission::code).toList();
        adminInMain = new AuthenticatedAccount(
                admin, "Staff member", AccountScope.TENANT, tenantId, List.of("ADMIN"), adminPermissions, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(REQUEST_ACCOUNT, adminInMain);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void adminAssignsReceptionInItsOwnBranch() {
        List<AssignmentView> result = asAdmin(() -> assignments.replace(target, perBranch(main, "RECEPTION"), adminInMain));

        assertThat(result).singleElement().satisfies(view -> assertThat(view.branchId()).isEqualTo(main));
    }

    @Test
    void adminOfMainCannotAssignReceptionWhereItIsOnlyDoctor() {
        assertRejected(() -> assignments.replace(target, perBranch(north, "RECEPTION"), adminInMain),
                ErrorCode.PRIVILEGE_ESCALATION);
        assertRejected(() -> assignments.replace(target, allBranches("RECEPTION"), adminInMain),
                ErrorCode.PRIVILEGE_ESCALATION);
        assertThat(staff.assignments(target)).isEmpty();
    }

    @Test
    void adminCannotAssignNorRemoveOwner() {
        assertRejected(() -> assignments.replace(target, allBranches("OWNER"), adminInMain), ErrorCode.PRIVILEGE_ESCALATION);

        UUID owner = staff.ownerOf(tenantId);
        assertRejected(() -> assignments.replace(owner, perBranch(main, "RECEPTION"), adminInMain),
                ErrorCode.PRIVILEGE_ESCALATION);
        assertThat(staff.assignments(owner)).singleElement()
                .satisfies(assignment -> assertThat(assignment.get("branch_id")).isNull());
    }

    @Test
    void adminCannotChangeItsOwnAssignments() {
        assertRejected(() -> assignments.replace(admin, perBranch(north, "ADMIN"), adminInMain),
                ErrorCode.SELF_MODIFICATION_FORBIDDEN);
    }

    @Test
    void onlyAnOwnerEditsAdminAndNobodyEditsARoleAssignedToThemselves() {
        UUID adminRole = staff.role(tenantId, "ADMIN");
        UUID doctorRole = staff.role(tenantId, "DOCTOR");

        assertRejected(() -> roles.update(adminRole, new RoleCommand("ADMIN", null, List.of("roles.read")), 0, adminInMain),
                ErrorCode.PRIVILEGE_ESCALATION);
        assertRejected(() -> roles.update(doctorRole, new RoleCommand("Médico", null, List.of("patients.read")), 0, adminInMain),
                ErrorCode.SELF_MODIFICATION_FORBIDDEN);
    }

    @Test
    void addingPermissionsToARoleAssignedWhereTheCallerLacksThemIsRejected() {
        UUID nurse = staff.role(tenantId, "NURSE");
        staff.assign(tenantId, target, north, nurse);

        assertRejected(() -> roles.update(nurse, new RoleCommand("Enfermería", null, List.of("patients.register")), 0, adminInMain),
                ErrorCode.PRIVILEGE_ESCALATION);
    }

    @Test
    void permissionCheckerAnswersPerBranch() {
        assertThat(asAdmin(() -> permissionChecker.hasPermissionInBranch(Permission.PATIENTS_REGISTER, main))).isTrue();
        assertThat(asAdmin(() -> permissionChecker.hasPermissionInBranch(Permission.PATIENTS_REGISTER, north))).isFalse();
        assertThat(asAdmin(() -> permissionChecker.hasPermissionInBranch(Permission.PATIENTS_READ, north))).isFalse();
        assertThat(asAdmin(() -> permissionChecker.hasPermissionInBranch(Permission.PLATFORM_PERMISSIONS_READ, main))).isFalse();
        assertThat(asAdmin(() -> permissionChecker.hasPermission(Permission.ROLES_READ))).isTrue();
        asAdmin(() -> {
            permissionChecker.requirePermission(Permission.ROLES_READ);
            return null;
        });
    }

    private RoleAssignmentsRequest perBranch(UUID branchId, String roleKey) {
        return new RoleAssignmentsRequest(RoleAssignmentsRequest.Mode.PER_BRANCH, null,
                List.of(new RoleAssignmentsRequest.BranchRole(branchId, staff.role(tenantId, roleKey))));
    }

    private RoleAssignmentsRequest allBranches(String roleKey) {
        return new RoleAssignmentsRequest(RoleAssignmentsRequest.Mode.ALL_BRANCHES, staff.role(tenantId, roleKey), null);
    }

    private <T> T asAdmin(Supplier<T> work) {
        return TenantContext.callAs(tenantId, work);
    }

    private void assertRejected(Runnable call, ErrorCode code) {
        assertThatThrownBy(() -> asAdmin(() -> {
            call.run();
            return null;
        })).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
