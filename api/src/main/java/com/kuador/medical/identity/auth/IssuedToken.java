package com.kuador.medical.identity.auth;

/** A token (signed JWT or opaque refresh token) and its lifetime in seconds. Never printed. */
public record IssuedToken(String value, long expiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedToken[redacted]";
    }
}
