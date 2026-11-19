package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;

/** Body of {@code POST /auth/login}. */
public record LoginResponse(
        String accessToken, String tokenType, long expiresIn, AccountScope scope, boolean mustChangePassword) {

    LoginResponse(String accessToken, long expiresIn, AccountScope scope, boolean mustChangePassword) {
        this(accessToken, "Bearer", expiresIn, scope, mustChangePassword);
    }

    @Override
    public String toString() {
        return "LoginResponse[redacted]";
    }
}
