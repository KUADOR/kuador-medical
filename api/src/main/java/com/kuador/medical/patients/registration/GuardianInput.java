package com.kuador.medical.patients.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Guardian of a minor being registered: exactly one of {@code guardianId} or {@code person}. */
record GuardianInput(
        @Nullable UUID guardianId,
        @Nullable @Valid GuardianPersonInput person,
        @NotBlank @Size(max = 60) String relationship) {

    @Override
    public String toString() {
        return "GuardianInput[redacted]";
    }
}
