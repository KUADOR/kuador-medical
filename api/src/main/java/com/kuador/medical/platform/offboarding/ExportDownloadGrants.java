package com.kuador.medical.platform.offboarding;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Short-lived, signed download links of a ready export ({@code downloadUrl}, like a presigned URL):
 * the grant carries the clinic, the export, the account that asked for it and its expiry, signed
 * with HMAC-SHA256. The browser opens the link without an access token, so the clinic never
 * travels as a parameter of its own (ADR-007) and the download is audited with the account that
 * got the link. Signing key: a key derived from the configured secret, used only for this.
 */
final class ExportDownloadGrants {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DERIVATION_LABEL = "kuador-medical/tenant-export-download/v1".getBytes(StandardCharsets.UTF_8);
    private static final int PAYLOAD_BYTES = 16 * 3 + Long.BYTES;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;

    ExportDownloadGrants(byte[] secret) {
        if (secret.length < 32) {
            throw new IllegalStateException("The export download signing key needs at least 32 bytes");
        }
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(secret, ALGORITHM), DERIVATION_LABEL), ALGORITHM);
    }

    /** What a valid grant allows: downloading {@code exportId} of {@code tenantId}, as {@code accountId}. */
    record Grant(UUID tenantId, UUID exportId, UUID accountId, Instant expiresAt) {}

    String issue(Grant grant) {
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES);
        put(payload, grant.tenantId());
        put(payload, grant.exportId());
        put(payload, grant.accountId());
        payload.putLong(grant.expiresAt().getEpochSecond());
        byte[] bytes = payload.array();
        return ENCODER.encodeToString(bytes) + "." + ENCODER.encodeToString(hmac(key, bytes));
    }

    /** The grant, if it is well formed, signed with this key and not expired at {@code now}. */
    Optional<Grant> verify(String value, Instant now) {
        int dot = value.indexOf('.');
        if (dot <= 0 || value.length() > 200) {
            return Optional.empty();
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = DECODER.decode(value.substring(0, dot));
            signature = DECODER.decode(value.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (payload.length != PAYLOAD_BYTES || !MessageDigest.isEqual(hmac(key, payload), signature)) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Grant grant = new Grant(uuid(buffer), uuid(buffer), uuid(buffer), Instant.ofEpochSecond(buffer.getLong()));
        return now.isBefore(grant.expiresAt()) ? Optional.of(grant) : Optional.empty();
    }

    private static void put(ByteBuffer buffer, UUID id) {
        buffer.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(ByteBuffer buffer) {
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] hmac(SecretKeySpec key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
