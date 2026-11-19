package com.kuador.medical.identity.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Access token settings (ADR-006): {@code KUADOR_JWT_SECRET} signs the tokens (HS256, at least
 * 256 bits) and {@code KUADOR_JWT_TTL} sets their lifetime. The secret is never logged.
 */
@ConfigurationProperties("kuador.jwt")
public record JwtProperties(String secret, @DefaultValue("PT30M") Duration ttl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "kuador.jwt.secret (KUADOR_JWT_SECRET) is missing or shorter than " + MIN_SECRET_BYTES + " bytes");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("kuador.jwt.ttl (KUADOR_JWT_TTL) must be a positive duration");
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=redacted, ttl=" + ttl + "]";
    }
}
