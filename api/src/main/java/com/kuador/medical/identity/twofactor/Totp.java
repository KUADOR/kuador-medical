package com.kuador.medical.identity.twofactor;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords of RFC 6238 (ADR-019): HMAC-SHA1, 6 digits, 30-second steps and
 * a window of one step before and after, implemented with {@code javax.crypto} only. Pure
 * functions: the caller keeps the last step used, so that a code is accepted once.
 */
final class Totp {

    static final int DIGITS = 6;
    static final int PERIOD_SECONDS = 30;
    static final String ALGORITHM = "SHA1";

    private static final int SECRET_BYTES = 20;
    private static final int WINDOW = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** A new 160-bit secret (RFC 4226 recommends the HMAC-SHA1 output length). */
    static byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
    }

    static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    /**
     * The step whose code is {@code code}, within the window around {@code now} and after
     * {@code lastUsedStep}; empty for a wrong, malformed, expired or already used code. Every
     * candidate is compared in constant time.
     */
    static OptionalLong match(byte[] secret, String code, Instant now, long lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        long current = step(now);
        OptionalLong matched = OptionalLong.empty();
        for (long candidate = current - WINDOW; candidate <= current + WINDOW; candidate++) {
            boolean equal = MessageDigest.isEqual(code(secret, candidate).getBytes(), code.getBytes());
            if (equal && candidate > lastUsedStep && matched.isEmpty()) {
                matched = OptionalLong.of(candidate);
            }
        }
        return matched;
    }
}
