package com.kuador.medical.identity.account;

import com.kuador.medical.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-use tokens of ADR-014: 256 random bits, stored only as a SHA-256 hash with purpose,
 * clinic and expiry, and marked used when consumed. Native SQL with explicit filters (ADR-007):
 * {@link #findValid} is the only lookup without a clinic, because the token arrives before the
 * clinic is known and only its holder knows it; every other statement filters by the clinic
 * bound in {@link TenantContext}. Tokens are never logged.
 */
@Component
public class OneTimeTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    OneTimeTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A valid token found by its value: the clinic and account it belongs to. */
    public record ValidToken(UUID id, UUID tenantId, UUID userId, Instant expiresAt) {}

    /** A token just issued: its value (only for the email) and its expiry. */
    public record IssuedToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }

    /** Issues a token of {@code purpose} for an account of the current clinic. */
    public IssuedToken issue(UUID userId, OneTimeTokenPurpose purpose, Duration ttl) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        jdbc.update("""
                insert into identity_one_time_token (id, tenant_id, user_id, purpose, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), currentTenant(), userId, purpose.name(), hash(value),
                Timestamp.from(expiresAt), Timestamp.from(now));
        return new IssuedToken(value, expiresAt);
    }

    /** Marks every pending token of {@code purpose} of the account as used, so none of them works any more. */
    public void invalidatePending(UUID userId, OneTimeTokenPurpose purpose) {
        jdbc.update("""
                update identity_one_time_token set used_at = ?
                where tenant_id = ? and user_id = ? and purpose = ? and used_at is null""",
                Timestamp.from(clock.instant()), currentTenant(), userId, purpose.name());
    }

    /** Expiry of the pending (unused, unexpired) token of {@code purpose} of the account, if any. */
    public Optional<Instant> pendingExpiry(UUID userId, OneTimeTokenPurpose purpose) {
        List<Timestamp> found = jdbc.queryForList("""
                select max(expires_at) from identity_one_time_token
                where tenant_id = ? and user_id = ? and purpose = ? and used_at is null and expires_at > ?""",
                Timestamp.class, currentTenant(), userId, purpose.name(), Timestamp.from(clock.instant()));
        return found.stream().filter(java.util.Objects::nonNull).findFirst().map(Timestamp::toInstant);
    }

    /** The unused, unexpired token of {@code purpose} with this value, in whatever clinic it belongs to. */
    public Optional<ValidToken> findValid(String value, OneTimeTokenPurpose purpose) {
        return jdbc.query("""
                        select id, tenant_id, user_id, expires_at from identity_one_time_token
                        where token_hash = ? and purpose = ? and used_at is null and expires_at > ?""",
                        (row, index) -> new ValidToken(
                                row.getObject("id", UUID.class),
                                row.getObject("tenant_id", UUID.class),
                                row.getObject("user_id", UUID.class),
                                row.getTimestamp("expires_at").toInstant()),
                        hash(value), purpose.name(), Timestamp.from(clock.instant()))
                .stream().findFirst();
    }

    /** Consumes a token of the current clinic; false when someone consumed it first. */
    public boolean consume(UUID tokenId) {
        return jdbc.update("""
                update identity_one_time_token set used_at = ?
                where id = ? and tenant_id = ? and used_at is null and expires_at > ?""",
                Timestamp.from(clock.instant()), tokenId, currentTenant(), Timestamp.from(clock.instant())) == 1;
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static UUID currentTenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }
}
