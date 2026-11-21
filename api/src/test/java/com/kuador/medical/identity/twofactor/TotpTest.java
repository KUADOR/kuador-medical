package com.kuador.medical.identity.twofactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** TOTP of RFC 6238 (ADR-019): HMAC-SHA1, 6 digits, 30-second steps, window of one step. */
class TotpTest {

    /** Shared secret of the RFC 6238 appendix B test vectors for SHA-1. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** The RFC lists 8-digit codes; the last 6 digits are the 6-digit code. */
    @ParameterizedTest
    @CsvSource({
        "59, 287082",
        "1111111109, 081804",
        "1111111111, 050471",
        "1234567890, 005924",
        "2000000000, 279037",
        "20000000000, 353130"
    })
    void matchesTheRfc6238TestVectors(long epochSeconds, String expected) {
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(epochSeconds)))).isEqualTo(expected);
    }

    @Test
    void acceptsTheCurrentThePreviousAndTheNextStepOnly() {
        Instant now = Instant.ofEpochSecond(1_111_111_111);
        long step = Totp.step(now);

        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step), now, -1)).isEqualTo(OptionalLong.of(step));
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, -1)).isEqualTo(OptionalLong.of(step - 1));
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, -1)).isEqualTo(OptionalLong.of(step + 1));
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, -1)).isEmpty();
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, -1)).isEmpty();
    }

    @Test
    void rejectsACodeOfAStepAlreadyUsed() {
        Instant now = Instant.ofEpochSecond(1_111_111_111);
        long step = Totp.step(now);
        String code = Totp.code(RFC_SECRET, step);

        assertThat(Totp.match(RFC_SECRET, code, now, step)).as("same step, replayed").isEmpty();
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, step)).as("older step").isEmpty();
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, step)).isEqualTo(OptionalLong.of(step + 1));
    }

    @Test
    void rejectsMalformedCodes() {
        Instant now = Instant.ofEpochSecond(59);
        for (String code : new String[] {"", "28708", "2870822", "28708a", " 287082"}) {
            assertThat(Totp.match(RFC_SECRET, code, now, -1)).as("'%s'", code).isEmpty();
        }
    }

    @Test
    void newSecretsHave160RandomBits() {
        byte[] first = Totp.newSecret();
        byte[] second = Totp.newSecret();

        assertThat(first).hasSize(20);
        assertThat(first).isNotEqualTo(second);
    }
}
