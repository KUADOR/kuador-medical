package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opaque rotating refresh tokens (ADR-013), stored only as SHA-256 hashes. All the rotations of
 * one login share a family: presenting an already rotated token revokes the whole family (the
 * token was probably stolen) and is audited.
 *
 * <p>A token row is found by the hash of a secret only its holder knows, before any clinic is
 * bound, so this uses native SQL filtered by that hash (ADR-007); the clinic of the row is then
 * bound by the caller to issue the new tokens.
 */
@Component
class RefreshTokens {

    /** The session a refresh token belongs to. {@code tenantId} is null for the platform account. */
    record Session(@Nullable UUID tenantId, UUID userId, @Nullable UUID branchId, UUID familyId) {}

    private final JdbcTemplate jdbc;
    private final JwtProperties properties;
    private final TransactionTemplate ownTransaction;
    private final AuditRecorder audit;
    private final Clock clock = Clock.systemUTC();

    RefreshTokens(
            JdbcTemplate jdbc, JwtProperties properties, PlatformTransactionManager transactions, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.audit = audit;
    }

    static UUID newFamily() {
        return UUID.randomUUID();
    }

    /** Stores a new refresh token of the session (joins the transaction of the caller). */
    IssuedToken issue(@Nullable UUID tenantId, UUID userId, @Nullable UUID branchId, UUID familyId) {
        String token = SecureTokens.newToken();
        Instant now = clock.instant();
        jdbc.update("""
                insert into identity_refresh_token (id, tenant_id, user_id, branch_id, family_id, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), tenantId, userId, branchId, familyId, SecureTokens.hash(token),
                at(now.plus(properties.refreshTtl())), at(now));
        return new IssuedToken(token, properties.refreshTtl().toSeconds());
    }

    /**
     * Revokes a valid token and returns its session, in a transaction of its own. A revoked token
     * revokes its whole family; unknown, expired and revoked tokens are rejected the same way.
     *
     * @throws com.kuador.medical.shared.web.ApiException {@code INVALID_SESSION_TOKEN}
     */
    Session rotate(String token) {
        Optional<Session> rotated = ownTransaction.execute(status -> {
            List<StoredToken> found = jdbc.query("""
                    select id, tenant_id, user_id, branch_id, family_id, expires_at, revoked_at
                    from identity_refresh_token where token_hash = ? for update""",
                    (row, index) -> new StoredToken(
                            row.getObject(1, UUID.class),
                            new Session(row.getObject(2, UUID.class), row.getObject(3, UUID.class),
                                    row.getObject(4, UUID.class), row.getObject(5, UUID.class)),
                            row.getObject(6, OffsetDateTime.class).toInstant(),
                            row.getObject(7, OffsetDateTime.class) != null),
                    SecureTokens.hash(token));
            if (found.isEmpty()) {
                return Optional.<Session>empty();
            }
            StoredToken stored = found.getFirst();
            if (stored.revoked()) {
                revokeFamilyAfterReuse(stored.session());
                return Optional.<Session>empty();
            }
            if (!stored.expiresAt().isAfter(clock.instant())) {
                return Optional.<Session>empty();
            }
            jdbc.update("update identity_refresh_token set revoked_at = ? where id = ?", at(clock.instant()), stored.id());
            return Optional.of(stored.session());
        });
        return rotated.orElseThrow(SessionErrors::invalidSessionToken);
    }

    /** Revokes a token of {@code userId}, if valid, and returns its family (joins the caller's transaction). */
    Optional<UUID> revokeOwned(String token, UUID userId) {
        List<UUID> families = jdbc.queryForList("""
                update identity_refresh_token set revoked_at = ?
                where token_hash = ? and user_id = ? and revoked_at is null and expires_at > ?
                returning family_id""",
                UUID.class, at(clock.instant()), SecureTokens.hash(token), userId, at(clock.instant()));
        return families.stream().findFirst();
    }

    /** Logout: revokes the token, if it is still valid, and returns its session. */
    Optional<Session> revoke(String token) {
        return ownTransaction.execute(status -> jdbc.query("""
                update identity_refresh_token set revoked_at = ?
                where token_hash = ? and revoked_at is null
                returning tenant_id, user_id, branch_id, family_id""",
                (row, index) -> new Session(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                        row.getObject(3, UUID.class), row.getObject(4, UUID.class)),
                at(clock.instant()), SecureTokens.hash(token)).stream().findFirst());
    }

    /** Revokes every session of the account, for example after a password reset (joins the caller's transaction). */
    void revokeAllOf(UUID userId) {
        jdbc.update("update identity_refresh_token set revoked_at = ? where user_id = ? and revoked_at is null",
                at(clock.instant()), userId);
    }

    private void revokeFamilyAfterReuse(Session session) {
        int revoked = jdbc.update(
                "update identity_refresh_token set revoked_at = ? where family_id = ? and revoked_at is null",
                at(clock.instant()), session.familyId());
        if (revoked > 0) {
            audit.record(AuditAction.REFRESH_TOKEN_REUSED, session.tenantId(), session.userId(),
                    AuditTarget.account(session.userId()));
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record StoredToken(UUID id, Session session, Instant expiresAt, boolean revoked) {}
}
