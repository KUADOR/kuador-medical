package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.TwoFactorStep;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.identity.role.EffectivePermissions;
import com.kuador.medical.identity.twofactor.TwoFactorStore;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns verified token claims into the caller, reading the database on every request (ADR-008).
 * A TENANT caller must run with its clinic bound in {@link TenantContext}; it only gets clinic
 * permissions, and a PLATFORM caller only platform permissions (FR-014). An OWNER, or any account
 * with TOTP enabled, has a second factor pending until its session verifies the current one
 * (ADR-019): a reset by the SUPER_ADMIN ends the verified sessions on their next request.
 */
@Component
class AccountResolver {

    static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final UserAccountRepository accounts;
    private final ClinicAccountRepository clinicAccounts;
    private final TenantDirectory tenants;
    private final EffectivePermissions effectivePermissions;
    private final TwoFactorStore twoFactors;

    AccountResolver(
            UserAccountRepository accounts,
            ClinicAccountRepository clinicAccounts,
            TenantDirectory tenants,
            EffectivePermissions effectivePermissions,
            TwoFactorStore twoFactors) {
        this.accounts = accounts;
        this.clinicAccounts = clinicAccounts;
        this.tenants = tenants;
        this.effectivePermissions = effectivePermissions;
        this.twoFactors = twoFactors;
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedAccount> resolve(AccessTokenClaims claims) {
        return switch (claims.scope()) {
            case PLATFORM -> accounts.findById(claims.accountId())
                    .filter(UserAccount::isActive)
                    .filter(UserAccount::isPlatformAdmin)
                    .map(AccountResolver::platformCaller);
            case TENANT -> tenantCaller(claims);
        };
    }

    private static AuthenticatedAccount platformCaller(UserAccount account) {
        return new AuthenticatedAccount(
                account.getId(), account.getDisplayName(), AccountScope.PLATFORM, null, null,
                List.of(SUPER_ADMIN_ROLE), codes(Permission.ofScope(PermissionScope.PLATFORM), null),
                account.isMustChangePassword());
    }

    private Optional<AuthenticatedAccount> tenantCaller(AccessTokenClaims claims) {
        if (!TenantContext.current().equals(Optional.of(Objects.requireNonNull(claims.tenantId())))) {
            throw new IllegalStateException("The tenant of the token must be bound before resolving the caller");
        }
        if (tenants.find(claims.tenantId()).filter(TenantDirectory.TenantInfo::active).isEmpty()) {
            return Optional.empty();
        }
        // A disabled account stops working on its next request (ADR-008). ADR-013: the permissions
        // of the clinic-wide assignment plus those of the active branch, read now, so a change of
        // role or an assignment removed applies to this request.
        return clinicAccounts.findInCurrentTenant(claims.accountId())
                .filter(ClinicAccount::isActive)
                .map(account -> {
                    EffectivePermissions.Grant grant = effectivePermissions.of(account.getId(), claims.branchId());
                    return new AuthenticatedAccount(
                            account.getId(), account.getDisplayName(), AccountScope.TENANT, claims.tenantId(),
                            claims.branchId(), grant.roleNames(), grant.permissionCodes(), false,
                            pendingTwoFactor(account.getId(), grant.owner(), claims.twoFactorId()));
                });
    }

    /** {@code null} when the session passed the second factor or the account needs none. */
    private @Nullable TwoFactorStep pendingTwoFactor(UUID accountId, boolean owner, @Nullable UUID verified) {
        java.util.Optional<UUID> enabled = twoFactors.enabledFactorId(accountId);
        if (enabled.isEmpty()) {
            return owner ? TwoFactorStep.ENROLLMENT_REQUIRED : null;
        }
        return enabled.get().equals(verified) ? null : TwoFactorStep.VERIFICATION_REQUIRED;
    }

    /** Codes of {@code catalog} (in catalog order) that are granted; all of them when {@code granted} is null. */
    private static List<String> codes(List<Permission> catalog, @Nullable Set<String> granted) {
        return catalog.stream()
                .map(Permission::code)
                .filter(code -> granted == null || granted.contains(code))
                .toList();
    }
}
