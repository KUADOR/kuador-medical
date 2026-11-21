package com.kuador.medical.identity.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AES-GCM encryption of the TOTP secrets (ADR-019): never stored in clear, tampering detected. */
class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher(new TwoFactorProperties(randomKey(), "Medical Kuador"));

    @Test
    void roundTripsAndUsesAFreshNonceEveryTime() {
        byte[] secret = Totp.newSecret();

        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(secret);
        assertThat(cipher.decrypt(second)).isEqualTo(secret);
        assertThat(first).doesNotContain(Base32.encode(secret));
    }

    @Test
    void detectsTamperingAndAnotherKey() {
        String encrypted = cipher.encrypt(Totp.newSecret());
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(IllegalStateException.class);
        SecretCipher other = new SecretCipher(new TwoFactorProperties(randomKey(), "Medical Kuador"));
        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAMissingOrShortKey() {
        assertThatThrownBy(() -> new TwoFactorProperties("", "Medical Kuador"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KUADOR_TOTP_ENCRYPTION_KEY")
                .hasMessageNotContaining("short-key");
        assertThatThrownBy(() -> new TwoFactorProperties("short-key", "Medical Kuador"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String randomKey() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }
}
