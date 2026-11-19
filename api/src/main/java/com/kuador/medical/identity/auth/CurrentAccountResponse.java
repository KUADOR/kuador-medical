package com.kuador.medical.identity.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kuador.medical.identity.AccountScope;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code GET /me}; {@code tenant} is omitted for the PLATFORM scope. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record CurrentAccountResponse(
        UUID accountId,
        String displayName,
        AccountScope scope,
        @Nullable TenantSummary tenant,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword) {

    record TenantSummary(UUID id, String name) {}
}
