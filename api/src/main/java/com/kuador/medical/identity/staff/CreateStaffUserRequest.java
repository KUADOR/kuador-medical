package com.kuador.medical.identity.staff;

import com.kuador.medical.identity.role.RoleAssignmentsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /staff-users}; {@code id} is an optional client-generated id (offline-ready). */
record CreateStaffUserRequest(
        @Nullable UUID id,
        @NotBlank @Size(max = 120) String firstNames,
        @NotBlank @Size(max = 120) String lastNames,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String jobTitle,
        @Nullable @Size(min = 1, max = 60) String professionalRegistration,
        @NotNull @Valid RoleAssignmentsRequest roleAssignments) {

    @Override
    public String toString() {
        return "CreateStaffUserRequest[redacted]";
    }
}
