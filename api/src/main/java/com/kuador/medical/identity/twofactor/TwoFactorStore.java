package com.kuador.medical.identity.twofactor;

import com.kuador.medical.shared.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rows of {@code identity_two_factor} of the current clinic (ADR-007: every query is filtered by
 * the clinic bound in {@link TenantContext}). The current factor of an account is its only row not
 * reset: pending until {@code enabled_at}, enabled afterwards.
 */
@Component
public class TwoFactorStore {

    /** The current factor of an account. */
    record Factor(
            UUID id,
            UUID userId,
            String secretEncrypted,
            @Nullable Instant enabledAt,
            long lastUsedStep,
            int failedAttempts,
            @Nullable Instant lockedUntil) {

        boolean enabled() {
            return enabledAt != null;
        }

        @Override
        public String toString() {
            return "Factor[id=" + id + ", redacted]";
        }
    }

    private final JdbcTemplate jdbc;

    TwoFactorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Id of the enabled factor of the account in the current clinic, if any. */
    public Optional<UUID> enabledFactorId(UUID userId) {
        return current(userId).filter(Factor::enabled).map(Factor::id);
    }

    Optional<Factor> current(UUID userId) {
        return jdbc.query("""
                select id, user_id, secret_encrypted, enabled_at, last_used_step, failed_attempts, locked_until
                from identity_two_factor where tenant_id = ? and user_id = ? and reset_at is null""",
                TwoFactorStore::factor, tenant(), userId).stream().findFirst();
    }

    /** Same as {@link #current} but locks the row until the end of the transaction. */
    Optional<Factor> currentForUpdate(UUID userId) {
        return jdbc.query("""
                select id, user_id, secret_encrypted, enabled_at, last_used_step, failed_attempts, locked_until
                from identity_two_factor where tenant_id = ? and user_id = ? and reset_at is null for update""",
                TwoFactorStore::factor, tenant(), userId).stream().findFirst();
    }

    /** Replaces a pending enrollment (never an enabled factor) with a new one. */
    UUID startEnrollment(UUID userId, String secretEncrypted, Instant now) {
        jdbc.update("delete from identity_two_factor where tenant_id = ? and user_id = ? and reset_at is null and enabled_at is null",
                tenant(), userId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into identity_two_factor (id, tenant_id, user_id, secret_encrypted, created_at)
                values (?, ?, ?, ?, ?)""",
                id, tenant(), userId, secretEncrypted, at(now));
        return id;
    }

    void enable(UUID factorId, long usedStep, Instant now) {
        jdbc.update("""
                update identity_two_factor set enabled_at = ?, last_used_step = ?, failed_attempts = 0, locked_until = null
                where id = ? and tenant_id = ?""",
                at(now), usedStep, factorId, tenant());
    }

    void markUsed(UUID factorId, long usedStep) {
        jdbc.update("""
                update identity_two_factor set last_used_step = ?, failed_attempts = 0, locked_until = null
                where id = ? and tenant_id = ?""",
                usedStep, factorId, tenant());
    }

    /** Counts a wrong code; returns the failures so far. */
    int recordFailure(UUID factorId) {
        return jdbc.queryForObject("""
                update identity_two_factor set failed_attempts = failed_attempts + 1
                where id = ? and tenant_id = ? returning failed_attempts""",
                Integer.class, factorId, tenant());
    }

    void lock(UUID factorId, Instant until) {
        jdbc.update("update identity_two_factor set failed_attempts = 0, locked_until = ? where id = ? and tenant_id = ?",
                at(until), factorId, tenant());
    }

    /** Ends the current factor (pending or enabled) of the account; returns whether there was one. */
    boolean reset(UUID userId, UUID actor, String reason, Instant now) {
        return jdbc.update("""
                update identity_two_factor set reset_at = ?, reset_by = ?, reset_reason = ?
                where tenant_id = ? and user_id = ? and reset_at is null""",
                at(now), actor, reason, tenant(), userId) > 0;
    }

    private static Factor factor(ResultSet rs, int row) throws SQLException {
        Long lastUsed = rs.getObject("last_used_step", Long.class);
        return new Factor(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("secret_encrypted"),
                instant(rs.getObject("enabled_at", OffsetDateTime.class)),
                lastUsed == null ? -1 : lastUsed,
                rs.getInt("failed_attempts"),
                instant(rs.getObject("locked_until", OffsetDateTime.class)));
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static UUID tenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }
}
