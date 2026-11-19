package com.kuador.medical.identity.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The minimum length of {@code newPassword} is a policy rule (WEAK_PASSWORD), not a validation one. */
record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword, @NotNull @Size(max = 128) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
