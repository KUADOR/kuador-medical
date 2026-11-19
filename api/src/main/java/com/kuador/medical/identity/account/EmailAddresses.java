package com.kuador.medical.identity.account;

import java.util.Locale;

/** Emails are unique on the platform and compared case-insensitively (ADR-010). */
public final class EmailAddresses {

    private EmailAddresses() {}

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
