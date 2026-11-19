package com.kuador.medical.identity.role;

import com.kuador.medical.identity.Permission;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code Role} of the contract. */
record RoleResponse(
        UUID id,
        String name,
        @Nullable String description,
        @Nullable SystemRole systemKey,
        boolean systemRole,
        boolean clinical,
        List<String> permissions,
        long version) {

    static RoleResponse of(RoleView role) {
        return new RoleResponse(
                role.id(), role.name(), role.description(), role.systemKey(), role.systemRole(), role.clinical(),
                role.permissions().stream().map(Permission::code).toList(), role.version());
    }
}
