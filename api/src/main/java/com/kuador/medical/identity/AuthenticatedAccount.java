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
 * @param roles       role names ({@code SUPER_ADMIN} for the platform account)
 * @param permissions effective permission codes, in catalog order
 */
public record AuthenticatedAccount(
        UUID accountId,
        String displayName,
        AccountScope scope,
        @Nullable UUID tenantId,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword) {

    public AuthenticatedAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(scope, "scope");
        if ((scope == AccountScope.TENANT) != (tenantId != null)) {
            throw new IllegalArgumentException("A TENANT account needs a tenant and a PLATFORM account has none");
        }
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
