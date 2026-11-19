package com.kuador.medical.identity.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Initial SUPER_ADMIN account ({@code KUADOR_BOOTSTRAP_ADMIN_EMAIL} and
 * {@code KUADOR_BOOTSTRAP_ADMIN_PASSWORD}). The password is a secret: never logged nor printed.
 */
@ConfigurationProperties("kuador.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password) {

    boolean isComplete() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[redacted]";
    }
}
