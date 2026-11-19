package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.identity.membership.MembershipStatus;
import com.kuador.medical.identity.membership.TenantMembership;
import com.kuador.medical.identity.membership.TenantMembershipRepository;
import com.kuador.medical.identity.role.EffectivePermissions;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns verified token claims into the caller, reading the database on every request (ADR-008).
 * A TENANT caller must run with its clinic bound in {@link TenantContext}; it only gets clinic
 * permissions, and a PLATFORM caller only platform permissions (FR-014).
 */
@Component
class AccountResolver {

    static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final UserAccountRepository accounts;
    private final TenantMembershipRepository memberships;
    private final TenantDirectory tenants;
    private final EffectivePermissions effectivePermissions;

    AccountResolver(
            UserAccountRepository accounts,
            TenantMembershipRepository memberships,
            TenantDirectory tenants,
            EffectivePermissions effectivePermissions) {
        this.accounts = accounts;
        this.memberships = memberships;
        this.tenants = tenants;
        this.effectivePermissions = effectivePermissions;
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedAccount> resolve(AccessTokenClaims claims) {
        Optional<UserAccount> account = accounts.findById(claims.accountId()).filter(UserAccount::isActive);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        return switch (claims.scope()) {
            case PLATFORM -> account.filter(UserAccount::isPlatformAdmin).map(AccountResolver::platformCaller);
            case TENANT -> account.filter(found -> !found.isPlatformAdmin())
                    .flatMap(found -> tenantCaller(found, claims));
        };
    }

    private static AuthenticatedAccount platformCaller(UserAccount account) {
        return new AuthenticatedAccount(
                account.getId(), account.getDisplayName(), AccountScope.PLATFORM, null,
                List.of(SUPER_ADMIN_ROLE), codes(Permission.ofScope(PermissionScope.PLATFORM), null),
                account.isMustChangePassword());
    }

    private Optional<AuthenticatedAccount> tenantCaller(UserAccount account, AccessTokenClaims claims) {
        if (!TenantContext.current().equals(Optional.of(Objects.requireNonNull(claims.tenantId())))) {
            throw new IllegalStateException("The tenant of the token must be bound before resolving the caller");
        }
        if (tenants.find(claims.tenantId()).filter(TenantDirectory.TenantInfo::active).isEmpty()) {
            return Optional.empty();
        }
        Optional<TenantMembership> membership =
                memberships.findByUserAccountIdAndStatus(account.getId(), MembershipStatus.ACTIVE);
        // The token has no active branch yet (MEDK-15 adds it): only clinic-wide assignments apply.
        return membership.map(found -> {
            EffectivePermissions.Grant grant = effectivePermissions.of(account.getId(), null);
            return new AuthenticatedAccount(
                    account.getId(), account.getDisplayName(), AccountScope.TENANT, claims.tenantId(),
                    grant.roleNames(), grant.permissionCodes(), account.isMustChangePassword());
        });
    }

    /** Codes of {@code catalog} (in catalog order) that are granted; all of them when {@code granted} is null. */
    private static List<String> codes(List<Permission> catalog, @Nullable Set<String> granted) {
        return catalog.stream()
                .map(Permission::code)
                .filter(code -> granted == null || granted.contains(code))
                .toList();
    }
}
