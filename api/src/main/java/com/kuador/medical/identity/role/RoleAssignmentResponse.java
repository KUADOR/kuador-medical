package com.kuador.medical.identity.role;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code RoleAssignment} of the contract; {@code branch} is null for the clinic-wide OWNER. */
public record RoleAssignmentResponse(@Nullable Summary branch, Summary role) {

    public record Summary(UUID id, String name) {}

    public static RoleAssignmentResponse of(AssignmentView view) {
        return new RoleAssignmentResponse(
                view.branchId() == null ? null : new Summary(view.branchId(), view.branchName()),
                new Summary(view.roleId(), view.roleName()));
    }
}
