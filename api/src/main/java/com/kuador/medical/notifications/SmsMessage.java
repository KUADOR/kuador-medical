package com.kuador.medical.notifications;

import java.util.Objects;

/**
 * A text message. It may carry one-time links, so {@link #toString()} never prints the phone
 * number nor the body (Constitution V).
 */
public record SmsMessage(String to, String body) {

    public SmsMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String toString() {
        return "SmsMessage[redacted]";
    }
}
