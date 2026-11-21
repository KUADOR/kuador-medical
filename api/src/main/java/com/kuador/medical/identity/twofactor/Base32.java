package com.kuador.medical.identity.twofactor;

import java.io.ByteArrayOutputStream;

/** Base32 of RFC 4648 (upper case, no padding), the encoding of TOTP secrets in authenticator apps. */
final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {}

    static String encode(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    /** @throws IllegalArgumentException for a character outside the alphabet */
    static byte[] decode(String encoded) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '=') {
                break;
            }
            int value = ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 character at " + i);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
