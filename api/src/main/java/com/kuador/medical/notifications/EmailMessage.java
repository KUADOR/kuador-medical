package com.kuador.medical.notifications;

import java.util.Objects;

/**
 * A plain-text email. The body may carry one-time links, so {@link #toString()} never prints the
 * recipient, the subject nor the body (Constitution V).
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String toString() {
        return "EmailMessage[redacted]";
    }
}
