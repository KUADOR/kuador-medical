package com.kuador.medical.identity.auth;

import java.util.Objects;

/** Who tries to log in: the email typed and the client address. Never printed. */
record LoginAttempt(String email, String clientAddress) {

    LoginAttempt {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(clientAddress, "clientAddress");
    }

    @Override
    public String toString() {
        return "LoginAttempt[redacted]";
    }
}
