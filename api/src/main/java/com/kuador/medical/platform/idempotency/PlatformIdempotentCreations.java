package com.kuador.medical.platform.idempotency;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Idempotency-Key} of platform creations ({@code /platform/**}), with the same rules as
 * the clinic one ({@code shared.idempotency.IdempotentCreations}): the same key with the same body
 * returns the resource created the first time, with another body 409 {@code IDEMPOTENCY_KEY_REUSED}.
 * Platform requests have no clinic, so keys are kept per SUPER_ADMIN account and operation, for
 * 24 hours, in the transaction of the creation.
 */
@Component
public class PlatformIdempotentCreations {

    static final Duration RETENTION = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock = Clock.systemUTC();

    PlatformIdempotentCreations(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Result of {@link #create}: the id of the resource and whether this call created it. */
    public record Outcome(UUID resourceId, boolean created) {}

    private record Previous(String requestHash, UUID resourceId, Instant createdAt) {}

    /**
     * Runs {@code creation} unless {@code actorId} already used {@code key} for {@code operation}.
     *
     * @param key      value of the header; {@code null} runs the creation without idempotency
     * @param request  body of the request, compared by hash with the one of the first call
     * @param creation creates the resource and returns its id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome create(UUID actorId, String operation, @Nullable String key, Object request, Supplier<UUID> creation) {
        if (key == null) {
            return new Outcome(creation.get(), true);
        }
        String requestHash = hash(request);
        Instant now = clock.instant();
        List<Previous> previous = jdbc.query("""
                select request_hash, resource_id, created_at from platform_idempotency_record
                where actor_id = ? and operation = ? and idempotency_key = ?""",
                (rs, row) -> new Previous(
                        rs.getString(1), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant()),
                actorId, operation, key);
        if (!previous.isEmpty() && previous.getFirst().createdAt().isAfter(now.minus(RETENTION))) {
            if (!previous.getFirst().requestHash().equals(requestHash)) {
                throw ApiException.conflict(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED, "The Idempotency-Key was used with a different request.");
            }
            return new Outcome(previous.getFirst().resourceId(), false);
        }
        if (!previous.isEmpty()) {
            jdbc.update("delete from platform_idempotency_record where actor_id = ? and operation = ? and idempotency_key = ?",
                    actorId, operation, key);
        }
        UUID resourceId = creation.get();
        jdbc.update("""
                insert into platform_idempotency_record
                    (id, actor_id, operation, idempotency_key, request_hash, resource_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), actorId, operation, key, requestHash, resourceId, Timestamp.from(now));
        return new Outcome(resourceId, true);
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
