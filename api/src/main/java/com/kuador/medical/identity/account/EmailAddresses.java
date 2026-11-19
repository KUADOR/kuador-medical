package com.kuador.medical.identity.account;

import java.util.Locale;

/** Emails are compared case-insensitively; clinic accounts are unique per clinic (ADR-012). */
public final class EmailAddresses {

    private EmailAddresses() {}

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** {@code j***@example.com}: first letter of the local part and the domain, for previews. */
    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
