package com.kuador.medical.identity.twofactor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts the TOTP secrets at rest with AES-256-GCM (ADR-019). The AES key is the SHA-256 of
 * {@code KUADOR_TOTP_ENCRYPTION_KEY}; every encryption uses a fresh 96-bit nonce, stored before
 * the ciphertext and its tag (Base64). Tampering or another key makes decryption fail.
 */
@Component
class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    SecretCipher(TwoFactorProperties properties) {
        try {
            byte[] material = MessageDigest.getInstance("SHA-256")
                    .digest(properties.encryptionKey().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(material, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    String encrypt(byte[] secret) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(secret);
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt the second factor secret", e);
        }
    }

    /** @throws IllegalStateException when the value was tampered with or encrypted with another key */
    byte[] decrypt(String encrypted) {
        try {
            byte[] raw = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, NONCE_BYTES));
            return cipher.doFinal(raw, NONCE_BYTES, raw.length - NONCE_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot decrypt the second factor secret", e);
        }
    }
}
