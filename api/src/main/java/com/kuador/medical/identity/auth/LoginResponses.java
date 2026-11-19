package com.kuador.medical.identity.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kuador.medical.identity.AccountScope;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Bodies of the login steps (contract {@code LoginResult}), discriminated by {@code status}. */
final class LoginResponses {

    private LoginResponses() {}

    static Object of(LoginResult result) {
        return switch (result) {
            case LoginResult.Authenticated authenticated -> authenticated(authenticated);
            case LoginResult.ClinicSelection selection -> new ClinicSelectionResponse(
                    "CLINIC_SELECTION_REQUIRED", selection.preAuth().value(), selection.preAuth().expiresInSeconds(),
                    selection.clinics());
            case LoginResult.BranchSelection selection -> new BranchSelectionResponse(
                    "BRANCH_SELECTION_REQUIRED", selection.preAuth().value(), selection.preAuth().expiresInSeconds(),
                    selection.clinic(), selection.branches());
            default -> throw new IllegalStateException("Unknown login result " + result.getClass().getName());
        };
    }

    static AuthenticatedResponse authenticated(LoginResult.Authenticated result) {
        return new AuthenticatedResponse(
                "AUTHENTICATED",
                result.accessToken().value(),
                "Bearer",
                result.accessToken().expiresInSeconds(),
                result.refreshToken().value(),
                result.refreshToken().expiresInSeconds(),
                result.scope(),
                result.clinic(),
                result.branch(),
                result.mustChangePassword());
    }

    /** {@code clinic} is omitted for the platform account; {@code branch} is always present, possibly null. */
    record AuthenticatedResponse(
            String status,
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn,
            AccountScope scope,
            @JsonInclude(JsonInclude.Include.NON_NULL) LoginResult.@Nullable Summary clinic,
            LoginResult.@Nullable Summary branch,
            boolean mustChangePassword) {

        @Override
        public String toString() {
            return "AuthenticatedResponse[redacted]";
        }
    }

    record ClinicSelectionResponse(
            String status, String preAuthToken, long preAuthExpiresIn, List<LoginResult.Summary> clinics) {

        @Override
        public String toString() {
            return "ClinicSelectionResponse[redacted]";
        }
    }

    record BranchSelectionResponse(
            String status,
            String preAuthToken,
            long preAuthExpiresIn,
            LoginResult.Summary clinic,
            List<LoginResult.Summary> branches) {

        @Override
        public String toString() {
            return "BranchSelectionResponse[redacted]";
        }
    }
}
