package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What an access token states (ADR-013): the account, its scope, its clinic and active branch,
 * and the permissions of that assignment when it was issued. The permissions in the token inform
 * the clients; the api resolves them again from the database on every request (ADR-008).
 */
record AccessGrant(
        UUID accountId,
        AccountScope scope,
        @Nullable UUID tenantId,
        @Nullable UUID branchId,
        List<String> permissions) {

    AccessGrant {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(scope, "scope");
        permissions = List.copyOf(permissions);
    }
}
