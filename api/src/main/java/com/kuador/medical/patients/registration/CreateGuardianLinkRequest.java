package com.kuador.medical.patients.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /patients/{id}/guardian-links}: exactly one of {@code guardianId} or {@code person}. */
record CreateGuardianLinkRequest(
        @Nullable UUID id,
        @Nullable UUID guardianId,
        @Nullable @Valid GuardianPersonInput person,
        @NotBlank @Size(max = 60) String relationship) {

    @Override
    public String toString() {
        return "CreateGuardianLinkRequest[redacted]";
    }
}
