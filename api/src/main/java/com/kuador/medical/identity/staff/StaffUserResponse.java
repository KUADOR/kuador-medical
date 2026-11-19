package com.kuador.medical.identity.staff;

import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.role.RoleAssignmentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** {@code StaffUser} of the contract. */
record StaffUserResponse(
        UUID id,
        String firstNames,
        String lastNames,
        String email,
        String jobTitle,
        @Nullable String professionalRegistration,
        ClinicAccountStatus status,
        List<RoleAssignmentResponse> roleAssignments,
        @Nullable Instant invitationExpiresAt,
        Instant createdAt,
        long version) {

    @Override
    public String toString() {
        return "StaffUserResponse[" + id + ", redacted]";
    }
}
