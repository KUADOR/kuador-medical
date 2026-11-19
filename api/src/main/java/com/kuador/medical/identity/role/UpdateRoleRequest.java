package com.kuador.medical.identity.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Body of {@code PUT /roles/{id}}: the full role plus the version the client read. */
record UpdateRoleRequest(
        @NotBlank @Size(max = RoleLimits.NAME_MAX) String name,
        @Size(max = RoleLimits.DESCRIPTION_MAX) @Nullable String description,
        @NotNull List<@NotNull String> permissions,
        @NotNull @PositiveOrZero Long version) {

    RoleCommand toCommand() {
        return new RoleCommand(name, description, permissions);
    }
}
