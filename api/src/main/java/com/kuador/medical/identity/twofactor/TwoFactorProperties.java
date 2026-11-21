package com.kuador.medical.identity.twofactor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code kuador.two-factor} (ADR-019): the key that encrypts the TOTP secrets
 * ({@code KUADOR_TOTP_ENCRYPTION_KEY}, a secret of at least 32 bytes kept in the secrets manager),
 * the issuer shown by authenticator apps, and the limit of wrong codes before a temporary lock.
 * The api refuses to start without a valid key.
 */
@ConfigurationProperties("kuador.two-factor")
public record TwoFactorProperties(
        String encryptionKey,
        @DefaultValue("Medical Kuador") String issuer,
        @DefaultValue("5") int maxFailures,
        @DefaultValue("PT15M") Duration lockDuration) {

    static final int MIN_KEY_BYTES = 32;

    @ConstructorBinding
    public TwoFactorProperties {
        if (encryptionKey == null || encryptionKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalStateException("kuador.two-factor.encryption-key (KUADOR_TOTP_ENCRYPTION_KEY) is missing or shorter than "
                    + MIN_KEY_BYTES + " bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "Medical Kuador";
        }
        if (maxFailures < 1) {
            maxFailures = 5;
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            lockDuration = Duration.ofMinutes(15);
        }
    }

    TwoFactorProperties(String encryptionKey, String issuer) {
        this(encryptionKey, issuer, 5, Duration.ofMinutes(15));
    }

    @Override
    public String toString() {
        return "TwoFactorProperties[encryptionKey=redacted, issuer=" + issuer + "]";
    }
}
