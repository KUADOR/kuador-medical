package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One step of the login flow (ADR-012, ADR-013). */
interface LoginResult {

    /** A clinic or a branch as the login shows it. */
    record Summary(UUID id, String name) {}

    /** Tokens issued: the session is bound to a clinic and, when it has one, a branch. */
    record Authenticated(
            IssuedToken accessToken,
            IssuedToken refreshToken,
            AccountScope scope,
            @Nullable Summary clinic,
            @Nullable Summary branch,
            boolean mustChangePassword) implements LoginResult {}

    /** The password matched several clinics: only those are listed. */
    record ClinicSelection(IssuedToken preAuth, List<Summary> clinics) implements LoginResult {

        public ClinicSelection {
            clinics = List.copyOf(clinics);
        }

        public String preAuthToken() {
            return preAuth.value();
        }
    }

    /** One clinic matched and the account has several branches there. */
    record BranchSelection(IssuedToken preAuth, Summary clinic, List<Summary> branches) implements LoginResult {

        public BranchSelection {
            branches = List.copyOf(branches);
        }
    }
}
