package com.kuador.medical.identity.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /roles}. Permission codes are checked against the catalog by the service. */
record CreateRoleRequest(
        @Nullable UUID id,
        @NotBlank @Size(max = RoleLimits.NAME_MAX) String name,
        @Size(max = RoleLimits.DESCRIPTION_MAX) @Nullable String description,
        @Nullable Boolean clinical,
        @NotNull List<@NotNull String> permissions) {

    RoleCommand toCommand() {
        return new RoleCommand(name, description, Boolean.TRUE.equals(clinical), permissions);
    }
}
