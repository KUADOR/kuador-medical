package com.kuador.medical.identity.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-use tokens of ADR-014 (password reset now; invitations and email verification in their
 * tasks): random, stored only as SHA-256 with purpose, clinic and expiry, marked used once
 * consumed. A new token of the same purpose supersedes the pending ones of the account in that
 * clinic. Found by the hash of the token before any clinic is bound, so native SQL filtered by
 * that hash (ADR-007). Every method joins the transaction of the caller.
 */
@Component
class OneTimeTokens {

    enum Purpose {
        INVITATION,
        PASSWORD_RESET,
        EMAIL_VERIFICATION
    }

    /** A valid token: its row, clinic and account. */
    record Valid(UUID id, UUID tenantId, UUID userId) {}

    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    OneTimeTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates a token for the account in the clinic and returns it in clear text, the only time it exists. */
    String issue(UUID tenantId, UUID userId, Purpose purpose, Duration ttl) {
        Instant now = clock.instant();
        jdbc.update("""
                update identity_one_time_token set used_at = ?
                where tenant_id = ? and user_id = ? and purpose = ? and used_at is null""",
                at(now), tenantId, userId, purpose.name());
        String token = SecureTokens.newToken();
        jdbc.update("""
                insert into identity_one_time_token (id, tenant_id, user_id, purpose, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tenantId, userId, purpose.name(), SecureTokens.hash(token), at(now.plus(ttl)), at(now));
        return token;
    }

    /** The token if it has that purpose, is unused and has not expired. */
    Optional<Valid> findValid(String token, Purpose purpose) {
        return jdbc.query("""
                select id, tenant_id, user_id from identity_one_time_token
                where token_hash = ? and purpose = ? and used_at is null and expires_at > ?""",
                (row, index) -> new Valid(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                        row.getObject(3, UUID.class)),
                SecureTokens.hash(token), purpose.name(), at(clock.instant())).stream().findFirst();
    }

    /** Marks the token used; false when another request used it first. */
    boolean markUsed(UUID id) {
        return jdbc.update("update identity_one_time_token set used_at = ? where id = ? and used_at is null",
                at(clock.instant()), id) == 1;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
