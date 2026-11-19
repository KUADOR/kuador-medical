package com.kuador.medical.identity.role;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code RoleAssignment} of the contract; {@code branch} is null for the clinic-wide OWNER. */
record RoleAssignmentResponse(@Nullable Summary branch, Summary role) {

    record Summary(UUID id, String name) {}

    static RoleAssignmentResponse of(AssignmentView view) {
        return new RoleAssignmentResponse(
                view.branchId() == null ? null : new Summary(view.branchId(), view.branchName()),
                new Summary(view.roleId(), view.roleName()));
    }
}
