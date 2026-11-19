package com.kuador.medical.identity.role;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Name, description, clinical flag and permission codes requested for a role; codes are checked by the service. */
public record RoleCommand(String name, @Nullable String description, boolean clinical, List<String> permissionCodes) {

    public RoleCommand {
        permissionCodes = List.copyOf(permissionCodes);
    }

    public RoleCommand(String name, @Nullable String description, List<String> permissionCodes) {
        this(name, description, false, permissionCodes);
    }
}
