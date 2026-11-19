package com.kuador.medical.identity.auth;

import com.kuador.medical.clinics.BranchDirectory.Branch;
import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.identity.PlanModules;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.shared.security.AllowedWhilePasswordChangeRequired;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.web.ApiException;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /me}: the caller, its clinic, active branch, branches it can switch to, the roles
 * and permissions of the active assignment and the modules the plan enables (from the platform).
 * It only informs the clients; every operation is still checked by the backend.
 */
@RestController
class MeController {

    private final CurrentUser currentUser;
    private final TenantDirectory tenants;
    private final BranchAccess branchAccess;
    private final PlanModules planModules;
    private final ClinicAccountRepository clinicAccounts;

    MeController(
            CurrentUser currentUser,
            TenantDirectory tenants,
            BranchAccess branchAccess,
            PlanModules planModules,
            ClinicAccountRepository clinicAccounts) {
        this.currentUser = currentUser;
        this.tenants = tenants;
        this.branchAccess = branchAccess;
        this.planModules = planModules;
        this.clinicAccounts = clinicAccounts;
    }

    @AuthenticatedEndpoint
    @AllowedWhilePasswordChangeRequired
    @GetMapping("/me")
    CurrentSessionResponse me() {
        AuthenticatedAccount account = currentUser.current().orElseThrow(ApiException::unauthenticated);
        if (account.scope() == AccountScope.PLATFORM) {
            return new CurrentSessionResponse(
                    account.accountId(), AccountKind.STAFF, account.scope(), account.displayName(), null, null,
                    List.of(), null, account.roles(), account.permissions(), List.of(), account.mustChangePassword());
        }
        LoginResult.Summary clinic = tenants.find(Objects.requireNonNull(account.tenantId()))
                .map(found -> new LoginResult.Summary(found.id(), found.name()))
                .orElseThrow(ApiException::unauthenticated);
        ClinicAccount clinicAccount = clinicAccounts.findInCurrentTenant(account.accountId())
                .orElseThrow(ApiException::unauthenticated);
        List<LoginResult.Summary> available = branchAccess.available(account.accountId()).stream()
                .map(MeController::summary)
                .toList();
        LoginResult.Summary activeBranch = available.stream()
                .filter(branch -> branch.id().equals(account.branchId()))
                .findFirst()
                .orElse(null);
        return new CurrentSessionResponse(
                account.accountId(), clinicAccount.getKind(), account.scope(), account.displayName(), clinic, activeBranch,
                available, clinicAccount.getKind() == AccountKind.PATIENT ? clinicAccount.getPatientRef() : null,
                account.roles(), account.permissions(), planModules.enabledModules(clinic.id()),
                account.mustChangePassword());
    }

    private static LoginResult.Summary summary(Branch branch) {
        return new LoginResult.Summary(branch.id(), branch.name());
    }
}
