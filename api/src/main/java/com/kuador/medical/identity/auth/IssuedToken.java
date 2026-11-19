package com.kuador.medical.identity.auth;

/** A signed access token and its lifetime in seconds. */
public record IssuedToken(String value, long expiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedToken[redacted]";
    }
}
