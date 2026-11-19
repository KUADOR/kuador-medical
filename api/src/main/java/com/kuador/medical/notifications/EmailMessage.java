package com.kuador.medical.notifications;

import java.util.Objects;

/**
 * A plain-text email. The address and the text may hold personal data and one-time links, so
 * {@link #toString()} never prints them.
 */
public record EmailMessage(String to, String subject, String text) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
    }

    @Override
    public String toString() {
        return "EmailMessage[redacted]";
    }
}
