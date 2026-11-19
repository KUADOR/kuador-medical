package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.clinics.BranchDirectory.Branch;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.TenantDirectory.TenantInfo;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.identity.membership.MembershipStatus;
import com.kuador.medical.identity.membership.TenantMembershipRepository;
import com.kuador.medical.identity.role.EffectivePermissions;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Starts, renews and moves sessions once the credentials (or a refresh token) are proven
 * (ADR-012, ADR-013). A clinic session is bound to a branch: the only one available, the one the
 * person chose, or none when the account has no branch. The access token carries the clinic,
 * the branch and the permissions of that assignment; the refresh token renews exactly that.
 * Every clinic step runs with the clinic bound, so all reads are filtered by it (ADR-007).
 */
@Service
class SessionIssuer {

    private final UserAccountRepository accounts;
    private final TenantMembershipRepository memberships;
    private final TenantDirectory tenants;
    private final BranchAccess branchAccess;
    private final EffectivePermissions permissions;
    private final AccessTokenService tokens;
    private final RefreshTokens refreshTokens;
    private final PreAuthTokens preAuthTokens;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;

    SessionIssuer(
            UserAccountRepository accounts,
            TenantMembershipRepository memberships,
            TenantDirectory tenants,
            BranchAccess branchAccess,
            EffectivePermissions permissions,
            AccessTokenService tokens,
            RefreshTokens refreshTokens,
            PreAuthTokens preAuthTokens,
            AuditRecorder audit,
            TransactionTemplate transaction) {
        this.accounts = accounts;
        this.memberships = memberships;
        this.tenants = tenants;
        this.branchAccess = branchAccess;
        this.permissions = permissions;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.preAuthTokens = preAuthTokens;
        this.audit = audit;
        this.transaction = transaction;
    }

    /** Session of the SUPER_ADMIN, without clinic nor branch. */
    LoginResult startPlatformSession(UUID accountId) {
        return inTransaction(() -> {
            UserAccount account = platformAccount(accountId).orElseThrow(SessionErrors::invalidCredentials);
            audit.record(AuditAction.LOGIN_SUCCEEDED, null, account.getId(), null);
            return platformSession(account, RefreshTokens.newFamily());
        });
    }

    /** Session in a clinic where the password matched; asks for the branch when there are several. */
    LoginResult startTenantSession(UUID tenantId, UUID accountId) {
        return TenantContext.callAs(tenantId, () -> inTransaction(() -> {
            ClinicMember member = member(tenantId, accountId).orElseThrow(SessionErrors::invalidCredentials);
            List<Branch> available = branchAccess.available(accountId);
            if (available.size() > 1) {
                return new LoginResult.BranchSelection(
                        preAuthTokens.issue(List.of(new PreAuthCandidate(tenantId, accountId))),
                        member.clinic(),
                        available.stream().map(SessionIssuer::summary).toList());
            }
            Branch branch = available.isEmpty() ? null : available.getFirst();
            audit.record(AuditAction.LOGIN_SUCCEEDED, tenantId, accountId, null);
            return tenantSession(member, branch, RefreshTokens.newFamily());
        }));
    }

    /** @throws ApiException {@code BRANCH_NOT_ASSIGNED} when the account cannot operate that branch */
    void requireAssignedBranch(UUID tenantId, UUID accountId, UUID branchId) {
        TenantContext.runAs(tenantId, () -> inTransaction(() -> assignedBranch(accountId, branchId)));
    }

    /** Session in the branch chosen after login. */
    LoginResult startBranchSession(UUID tenantId, UUID accountId, UUID branchId) {
        return TenantContext.callAs(tenantId, () -> inTransaction(() -> {
            ClinicMember member = member(tenantId, accountId).orElseThrow(SessionErrors::invalidSessionToken);
            Branch branch = assignedBranch(accountId, branchId);
            audit.record(AuditAction.LOGIN_SUCCEEDED, tenantId, accountId, null);
            return tenantSession(member, branch, RefreshTokens.newFamily());
        }));
    }

    /**
     * Rotates the refresh token and issues new tokens for the same clinic and branch, with the
     * current permissions. A disabled account, a suspended clinic or a branch no longer assigned
     * ends the session.
     */
    LoginResult.Authenticated refresh(String refreshToken) {
        RefreshTokens.Session session = refreshTokens.rotate(refreshToken);
        if (session.tenantId() == null) {
            return inTransaction(() -> platformSession(
                    platformAccount(session.userId()).orElseThrow(SessionErrors::invalidSessionToken),
                    session.familyId()));
        }
        return TenantContext.callAs(session.tenantId(), () -> inTransaction(() -> {
            ClinicMember member = member(session.tenantId(), session.userId())
                    .orElseThrow(SessionErrors::invalidSessionToken);
            Branch branch = null;
            if (session.branchId() != null) {
                branch = branchAccess.available(session.userId()).stream()
                        .filter(candidate -> candidate.id().equals(session.branchId()))
                        .findFirst()
                        .orElseThrow(SessionErrors::invalidSessionToken);
            }
            return tenantSession(member, branch, session.familyId());
        }));
    }

    /**
     * New tokens for another branch of the caller's clinic (the clinic is already bound by the
     * request). The given refresh token, if it is the caller's, is revoked and its family kept.
     */
    LoginResult.Authenticated switchBranch(AuthenticatedAccount caller, UUID branchId, @Nullable String refreshToken) {
        if (caller.scope() != AccountScope.TENANT || caller.tenantId() == null) {
            throw SessionErrors.branchNotAssigned();
        }
        UUID tenantId = caller.tenantId();
        return TenantContext.callAs(tenantId, () -> inTransaction(() -> {
            ClinicMember member = member(tenantId, caller.accountId()).orElseThrow(ApiException::unauthenticated);
            Branch branch = assignedBranch(caller.accountId(), branchId);
            UUID family = refreshToken == null || refreshToken.isBlank()
                    ? RefreshTokens.newFamily()
                    : refreshTokens.revokeOwned(refreshToken, caller.accountId()).orElseGet(RefreshTokens::newFamily);
            audit.record(AuditAction.BRANCH_SWITCHED, tenantId, caller.accountId(), AuditTarget.account(caller.accountId()));
            return tenantSession(member, branch, family);
        }));
    }

    /** Revokes the refresh token; unknown or already revoked tokens are ignored. */
    void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken).ifPresent(session ->
                audit.record(AuditAction.LOGOUT, session.tenantId(), session.userId(), null));
    }

    private LoginResult.Authenticated tenantSession(ClinicMember member, @Nullable Branch branch, UUID family) {
        UUID accountId = member.account().getId();
        UUID branchId = branch == null ? null : branch.id();
        EffectivePermissions.Grant grant = permissions.of(accountId, branchId);
        IssuedToken access = tokens.issue(new AccessGrant(
                accountId, AccountScope.TENANT, member.clinic().id(), branchId, grant.permissionCodes()));
        IssuedToken refresh = refreshTokens.issue(member.clinic().id(), accountId, branchId, family);
        return new LoginResult.Authenticated(access, refresh, AccountScope.TENANT, member.clinic(),
                branch == null ? null : summary(branch), member.account().isMustChangePassword());
    }

    private LoginResult.Authenticated platformSession(UserAccount account, UUID family) {
        List<String> platformPermissions =
                Permission.ofScope(PermissionScope.PLATFORM).stream().map(Permission::code).toList();
        IssuedToken access = tokens.issue(
                new AccessGrant(account.getId(), AccountScope.PLATFORM, null, null, platformPermissions));
        IssuedToken refresh = refreshTokens.issue(null, account.getId(), null, family);
        return new LoginResult.Authenticated(
                access, refresh, AccountScope.PLATFORM, null, null, account.isMustChangePassword());
    }

    private Branch assignedBranch(UUID accountId, UUID branchId) {
        return branchAccess.available(accountId).stream()
                .filter(branch -> branch.id().equals(branchId))
                .findFirst()
                .orElseThrow(SessionErrors::branchNotAssigned);
    }

    /** The account as an active member of an active clinic; the clinic must be bound. */
    private Optional<ClinicMember> member(UUID tenantId, UUID accountId) {
        Optional<TenantInfo> clinic = tenants.find(tenantId).filter(TenantInfo::active);
        Optional<UserAccount> account = accounts.findById(accountId)
                .filter(UserAccount::isActive)
                .filter(found -> !found.isPlatformAdmin());
        if (clinic.isEmpty() || account.isEmpty()
                || memberships.findByUserAccountIdAndStatus(accountId, MembershipStatus.ACTIVE).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ClinicMember(account.get(), new LoginResult.Summary(clinic.get().id(), clinic.get().name())));
    }

    private Optional<UserAccount> platformAccount(UUID accountId) {
        return accounts.findById(accountId).filter(UserAccount::isActive).filter(UserAccount::isPlatformAdmin);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return Objects.requireNonNull(transaction.execute(status -> work.get()));
    }

    private static LoginResult.Summary summary(Branch branch) {
        return new LoginResult.Summary(branch.id(), branch.name());
    }

    private record ClinicMember(UserAccount account, LoginResult.Summary clinic) {}
}
