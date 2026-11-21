package com.kuador.medical.identity;

/** What a session that has not passed the second factor must do (ADR-019, contract {@code Problem.twoFactor}). */
public enum TwoFactorStep {
    /** The account has no second factor yet: it enrolls TOTP. */
    ENROLLMENT_REQUIRED,
    /** The account has TOTP enabled: the session verifies a code. */
    VERIFICATION_REQUIRED
}
