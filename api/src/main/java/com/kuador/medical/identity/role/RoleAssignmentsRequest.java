package com.kuador.medical.identity.role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PUT /staff-users/{id}/role-assignments}, discriminated by {@code mode}:
 * {@code ALL_BRANCHES} carries {@code roleId}, {@code PER_BRANCH} carries {@code assignments}.
 */
public record RoleAssignmentsRequest(
        @NotNull Mode mode,
        @Nullable UUID roleId,
        @Nullable List<@Valid @NotNull BranchRole> assignments) {

    public enum Mode {
        ALL_BRANCHES,
        PER_BRANCH
    }

    public record BranchRole(@NotNull UUID branchId, @NotNull UUID roleId) {}
}
