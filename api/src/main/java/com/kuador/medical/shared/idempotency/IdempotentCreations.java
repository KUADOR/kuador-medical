package com.kuador.medical.shared.idempotency;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Idempotency-Key} of creation requests (plan of MEDK-12, rule 7): the same key with the
 * same body returns the resource created the first time; with another body it responds 409
 * {@code IDEMPOTENCY_KEY_REUSED}. Keys are kept per clinic and operation for 24 hours and are
 * recorded in the transaction of the creation, so a failed creation leaves no key behind.
 */
@Component
public class IdempotentCreations {

    static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository records;
    private final JsonMapper json;
    private final Clock clock = Clock.systemUTC();

    IdempotentCreations(IdempotencyRecordRepository records, JsonMapper json) {
        this.records = records;
        this.json = json;
    }

    /** Result of {@link #create}: the id of the resource and whether this call created it. */
    public record Outcome(UUID resourceId, boolean created) {}

    /**
     * Runs {@code creation} unless {@code key} was already used for {@code operation} in the
     * current clinic. Must run in the transaction of the creation, with the clinic bound.
     *
     * @param key      value of the header; {@code null} runs the creation without idempotency
     * @param request  body of the request, compared by hash with the one of the first call
     * @param creation creates the resource and returns its id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome create(String operation, @Nullable String key, Object request, Supplier<UUID> creation) {
        if (key == null) {
            return new Outcome(creation.get(), true);
        }
        String requestHash = hash(request);
        Instant now = clock.instant();
        var previous = records.find(operation, key);
        if (previous.isPresent() && previous.get().getCreatedAt().isAfter(now.minus(RETENTION))) {
            if (!previous.get().getRequestHash().equals(requestHash)) {
                throw ApiException.conflict(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED, "The Idempotency-Key was used with a different request.");
            }
            return new Outcome(previous.get().getResourceId(), false);
        }
        previous.ifPresent(expired -> records.delete(operation, key));
        UUID resourceId = creation.get();
        records.saveAndFlush(new IdempotencyRecord(operation, key, requestHash, resourceId, now));
        return new Outcome(resourceId, true);
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
