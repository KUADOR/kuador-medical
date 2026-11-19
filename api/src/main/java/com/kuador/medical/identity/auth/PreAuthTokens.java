package com.kuador.medical.identity.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pre-auth tokens of the login (ADR-012): an opaque random token that proves the password was
 * verified for the listed clinics only, valid for {@code kuador.jwt.pre-auth-ttl} and once. It
 * is stored only as a hash in {@code identity_one_time_token}, one row per clinic grouped by a
 * batch; the first row carries the hash of the token. Opaque, so its length does not grow with
 * the number of clinics. Found by that hash before any clinic is bound (native SQL, ADR-007).
 */
@Component
class PreAuthTokens {

    private static final String PURPOSE = "PRE_AUTH";

    private final JdbcTemplate jdbc;
    private final JwtProperties properties;
    private final TransactionTemplate ownTransaction;
    private final Clock clock = Clock.systemUTC();

    PreAuthTokens(JdbcTemplate jdbc, JwtProperties properties, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Stores a token for the candidates and returns it in clear text (joins the caller's transaction). */
    IssuedToken issue(List<PreAuthCandidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("A pre-auth token needs at least one clinic");
        }
        String token = SecureTokens.newToken();
        UUID batch = UUID.randomUUID();
        Instant now = clock.instant();
        for (int i = 0; i < candidates.size(); i++) {
            PreAuthCandidate candidate = candidates.get(i);
            String hash = i == 0 ? SecureTokens.hash(token) : SecureTokens.hash(token + "#" + candidate.tenantId());
            jdbc.update("""
                    insert into identity_one_time_token
                        (id, tenant_id, user_id, purpose, token_hash, expires_at, batch_id, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), candidate.tenantId(), candidate.accountId(), PURPOSE, hash,
                    at(now.plus(properties.preAuthTtl())), batch, at(now));
        }
        return new IssuedToken(token, properties.preAuthTtl().toSeconds());
    }

    /** The clinics of a valid (unused, unexpired) token, in the order they were listed. */
    Optional<PreAuthClaims> find(String token) {
        List<UUID> batches = jdbc.queryForList("""
                select batch_id from identity_one_time_token
                where token_hash = ? and purpose = ? and used_at is null and expires_at > ?""",
                UUID.class, SecureTokens.hash(token), PURPOSE, at(clock.instant()));
        if (batches.isEmpty() || batches.getFirst() == null) {
            return Optional.empty();
        }
        UUID batch = batches.getFirst();
        List<PreAuthCandidate> candidates = jdbc.query("""
                select tenant_id, user_id from identity_one_time_token
                where batch_id = ? and purpose = ? order by created_at, (token_hash = ?) desc""",
                (row, index) -> new PreAuthCandidate(row.getObject(1, UUID.class), row.getObject(2, UUID.class)),
                batch, PURPOSE, SecureTokens.hash(token));
        return Optional.of(new PreAuthClaims(batch, candidates));
    }

    /**
     * Uses the token up for every listed clinic, in a transaction of its own.
     *
     * @throws com.kuador.medical.shared.web.ApiException {@code INVALID_SESSION_TOKEN} when it was already used
     */
    void consume(PreAuthClaims claims, PreAuthCandidate chosen) {
        Integer used = ownTransaction.execute(status -> jdbc.update("""
                update identity_one_time_token set used_at = ?
                where batch_id = ? and purpose = ? and used_at is null""",
                at(clock.instant()), claims.batchId(), PURPOSE));
        if (used == null || used == 0 || !claims.candidates().contains(chosen)) {
            throw SessionErrors.invalidSessionToken();
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
