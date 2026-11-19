package com.kuador.medical.identity.role;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An assignment with the names of its branch and role; branch fields are null for the clinic-wide OWNER. */
public record AssignmentView(@Nullable UUID branchId, @Nullable String branchName, UUID roleId, String roleName) {}
