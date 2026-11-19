package com.kuador.medical.identity;

import java.util.Objects;

/** Person who signs up a clinic. The password is plain text only on its way to being hashed. */
public record NewTenantOwner(String displayName, String email, String password) {

    public NewTenantOwner {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
    }

    @Override
    public String toString() {
        return "NewTenantOwner[redacted]";
    }
}
