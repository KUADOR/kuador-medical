package com.kuador.medical.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Request bodies of the authentication endpoints. They carry secrets, so none is ever printed. */
final class AuthRequests {

    private AuthRequests() {}

    record SelectClinicRequest(@NotBlank @Size(max = 512) String preAuthToken, @NotNull UUID clinicId) {

        @Override
        public String toString() {
            return "SelectClinicRequest[redacted]";
        }
    }

    record SelectBranchRequest(@NotBlank @Size(max = 512) String preAuthToken, @NotNull UUID branchId) {

        @Override
        public String toString() {
            return "SelectBranchRequest[redacted]";
        }
    }

    record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[redacted]";
        }
    }

    record SwitchBranchRequest(@NotNull UUID branchId, @Nullable @Size(max = 512) String refreshToken) {

        @Override
        public String toString() {
            return "SwitchBranchRequest[redacted]";
        }
    }

    record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {

        @Override
        public String toString() {
            return "PasswordResetRequest[redacted]";
        }
    }

    record PasswordResetConfirmRequest(
            @NotBlank @Size(min = 16, max = 512) String token, @NotNull @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "PasswordResetConfirmRequest[redacted]";
        }
    }
}
