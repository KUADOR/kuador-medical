package com.kuador.medical.identity;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An account to invite for a patient or a guardian of the current clinic.
 *
 * @param patientRef patient of the patients module the account belongs to, if any (stored as an id)
 */
public record NewClinicAccount(
        AccountKind kind, String email, String firstNames, String lastNames, @Nullable UUID patientRef) {

    public NewClinicAccount {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(firstNames, "firstNames");
        Objects.requireNonNull(lastNames, "lastNames");
    }

    @Override
    public String toString() {
        return "NewClinicAccount[kind=" + kind + ", redacted]";
    }
}
