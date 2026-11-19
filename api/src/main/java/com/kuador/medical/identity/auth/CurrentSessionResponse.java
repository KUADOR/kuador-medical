package com.kuador.medical.identity.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kuador.medical.identity.AccountScope;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code GET /me} (contract {@code CurrentSession}). {@code clinic} is omitted for the
 * platform account and {@code patientId} for every account but patients; {@code activeBranch}
 * is always present, possibly null.
 */
record CurrentSessionResponse(
        UUID accountId,
        AccountKind kind,
        AccountScope scope,
        String displayName,
        @JsonInclude(JsonInclude.Include.NON_NULL) LoginResult.@Nullable Summary clinic,
        LoginResult.@Nullable Summary activeBranch,
        List<LoginResult.Summary> availableBranches,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable UUID patientId,
        List<String> roles,
        List<String> permissions,
        List<String> enabledModules,
        boolean mustChangePassword) {}
