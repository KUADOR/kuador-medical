package com.kuador.medical.identity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Caller of the current request, resolved from its access token and the database on every
 * request: a change of roles or password applies to the next request.
 *
 * @param tenantId    clinic of a {@code TENANT} account, {@code null} for {@code PLATFORM}
 * @param branchId    active branch of the session (ADR-013); {@code null} when the account does
 *                    not operate by branch, the clinic has none, or for {@code PLATFORM}
 * @param roles       role names ({@code SUPER_ADMIN} for the platform account)
 * @param permissions effective permission codes, in catalog order
 * @param twoFactorPending what the session must do before operating when it has not passed the
 *                    second factor (ADR-019); {@code null} when nothing is pending
 */
public record AuthenticatedAccount(
        UUID accountId,
        String displayName,
        AccountScope scope,
        @Nullable UUID tenantId,
        @Nullable UUID branchId,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword,
        @Nullable TwoFactorStep twoFactorPending) {

    public AuthenticatedAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(scope, "scope");
        if ((scope == AccountScope.TENANT) != (tenantId != null)) {
            throw new IllegalArgumentException("A TENANT account needs a tenant and a PLATFORM account has none");
        }
        if (branchId != null && tenantId == null) {
            throw new IllegalArgumentException("Only a TENANT account has an active branch");
        }
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    /** A caller with nothing pending on the second factor. */
    public AuthenticatedAccount(
            UUID accountId,
            String displayName,
            AccountScope scope,
            @Nullable UUID tenantId,
            @Nullable UUID branchId,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword) {
        this(accountId, displayName, scope, tenantId, branchId, roles, permissions, mustChangePassword, null);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
