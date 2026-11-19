package com.kuador.medical.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * The account of a patient who self-registers in the current clinic: it chose its password and
 * stays {@code PENDING_EMAIL_VERIFICATION} until the email is verified (ADR-014).
 *
 * @param patientRef patient of the patients module the account belongs to (stored as an id)
 */
public record NewSelfRegisteredAccount(
        String email, String password, String firstNames, String lastNames, UUID patientRef) {

    public NewSelfRegisteredAccount {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(firstNames, "firstNames");
        Objects.requireNonNull(lastNames, "lastNames");
        Objects.requireNonNull(patientRef, "patientRef");
    }

    @Override
    public String toString() {
        return "NewSelfRegisteredAccount[redacted]";
    }
}
