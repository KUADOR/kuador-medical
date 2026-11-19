package com.kuador.medical.identity.auth;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An active account able to log in with an email: its clinic ({@code null} for the platform
 * account) and its password hash. Never printed.
 */
record StoredCredential(UUID accountId, @Nullable UUID tenantId, String passwordHash, boolean platformAdmin) {

    @Override
    public String toString() {
        return "StoredCredential[redacted]";
    }
}
