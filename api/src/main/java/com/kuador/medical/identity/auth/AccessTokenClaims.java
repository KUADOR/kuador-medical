package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Verified content of an access token: who, which scope and, for TENANT, which clinic and branch,
 * and the second factor the session verified ({@code twoFactorId}, ADR-019), if any.
 */
public record AccessTokenClaims(
        UUID accountId, AccountScope scope, @Nullable UUID tenantId, @Nullable UUID branchId, @Nullable UUID twoFactorId) {

    public AccessTokenClaims(UUID accountId, AccountScope scope, @Nullable UUID tenantId, @Nullable UUID branchId) {
        this(accountId, scope, tenantId, branchId, null);
    }
}
