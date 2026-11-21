package com.kuador.medical;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Test side of the TOTP second factor (RFC 6238: HMAC-SHA1, 6 digits, 30 s), written apart from
 * the production code so that tests check it instead of reusing it. Plays the authenticator app.
 */
public final class TotpCodes {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpCodes() {}

    public static long currentStep() {
        return Instant.now().getEpochSecond() / 30;
    }

    /** Code of {@code step} for a Base32 secret as the enrollment returns it. */
    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String currentCode(String base32Secret) {
        return code(base32Secret, currentStep());
    }

    private static byte[] decode(String base32) {
        String clean = base32.replace("=", "");
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            buffer = (buffer << 5) | ALPHABET.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
