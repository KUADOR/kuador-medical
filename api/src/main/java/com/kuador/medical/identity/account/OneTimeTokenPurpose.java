package com.kuador.medical.identity.account;

/** What a one-time token is for (ADR-014); mirrors {@code identity_one_time_token_purpose_check}. */
public enum OneTimeTokenPurpose {
    INVITATION,
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
