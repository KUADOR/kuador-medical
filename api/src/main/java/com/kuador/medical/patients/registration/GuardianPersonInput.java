package com.kuador.medical.patients.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** A new guardian ({@code GuardianPersonInput} in the contract). */
record GuardianPersonInput(
        @NotBlank @Pattern(regexp = Requests.DOCUMENT_TYPE) String documentType,
        @NotBlank @Size(max = 30) String documentNumber,
        @NotBlank @Size(max = 120) String firstNames,
        @NotBlank @Size(max = 120) String lastNames,
        @Nullable @Email @Size(max = 254) String email,
        @Nullable @Pattern(regexp = Requests.PHONE) String phone) {

    @Override
    public String toString() {
        return "GuardianPersonInput[redacted]";
    }
}
