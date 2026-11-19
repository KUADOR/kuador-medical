package com.kuador.medical.identity.role;

import com.kuador.medical.identity.Permission;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A role with its permissions, in catalog order. */
public record RoleView(
        UUID id,
        String name,
        @Nullable String description,
        @Nullable SystemRole systemKey,
        boolean clinical,
        List<Permission> permissions,
        long version) {

    public RoleView {
        permissions = List.copyOf(permissions);
    }

    public boolean systemRole() {
        return systemKey != null;
    }
}
