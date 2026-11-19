package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Verified content of an access token: who, which scope and, for TENANT, which clinic and branch. */
public record AccessTokenClaims(
        UUID accountId, AccountScope scope, @Nullable UUID tenantId, @Nullable UUID branchId) {}
