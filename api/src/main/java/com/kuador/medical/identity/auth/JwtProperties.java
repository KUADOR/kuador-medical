package com.kuador.medical.identity.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token settings (ADR-006, ADR-012, ADR-013): {@code KUADOR_JWT_SECRET} signs the access and
 * pre-auth tokens (HS256, at least 256 bits); {@code ttl} is the access token lifetime,
 * {@code refreshTtl} the lifetime of the opaque rotating refresh token and {@code preAuthTtl}
 * the time between login and the clinic or branch choice. The secret is never logged.
 */
@ConfigurationProperties("kuador.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT15M") Duration ttl,
        @DefaultValue("P7D") Duration refreshTtl,
        @DefaultValue("PT5M") Duration preAuthTtl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "kuador.jwt.secret (KUADOR_JWT_SECRET) is missing or shorter than " + MIN_SECRET_BYTES + " bytes");
        }
        requirePositive(ttl, "kuador.jwt.ttl (KUADOR_JWT_TTL)");
        requirePositive(refreshTtl, "kuador.jwt.refresh-ttl (KUADOR_JWT_REFRESH_TTL)");
        requirePositive(preAuthTtl, "kuador.jwt.pre-auth-ttl (KUADOR_JWT_PRE_AUTH_TTL)");
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalStateException(name + " must be a positive duration");
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=redacted, ttl=" + ttl + ", refreshTtl=" + refreshTtl + ", preAuthTtl=" + preAuthTtl + "]";
    }
}
