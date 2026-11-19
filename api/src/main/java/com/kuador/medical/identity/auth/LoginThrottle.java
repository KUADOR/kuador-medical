package com.kuador.medical.identity.auth;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.TooManyAttemptsException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Attempt limit of the login and of the password reset requests (ADR-012, FR-006). Two counters
 * per attempt: email + client address, and email alone (every account of it, from any address).
 * Reaching {@code maxFailures} inside the window locks both for {@code lockDuration}.
 *
 * <p>Keys are HMACs of the email (keyed with the token secret), so the table holds no email in
 * clear text nor a plain hash that a list of emails could reverse. It has no tenant: it is
 * checked before any clinic is known. Every change commits on its own, so a failed login that
 * answers an error still counts.
 */
@Component
class LoginThrottle {

    private static final String HMAC = "HmacSHA256";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final LoginThrottleProperties properties;
    private final SecretKeySpec key;
    private final Clock clock;

    @Autowired
    LoginThrottle(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            LoginThrottleProperties properties,
            JwtProperties jwt) {
        this(jdbc, transactions, properties, jwt, Clock.systemUTC());
    }

    LoginThrottle(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            LoginThrottleProperties properties,
            JwtProperties jwt,
            Clock clock) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.properties = properties;
        this.key = new SecretKeySpec(("login-throttle:" + jwt.secret()).getBytes(StandardCharsets.UTF_8), HMAC);
        this.clock = clock;
    }

    /** 429 {@code TOO_MANY_ATTEMPTS} with the remaining lock time. */
    static ApiException tooManyAttempts(Duration retryAfter) {
        return new TooManyAttemptsException(retryAfter);
    }

    /** @throws ApiException {@code TOO_MANY_ATTEMPTS} while the login of the attempt is locked */
    void checkAllowed(LoginAttempt attempt) {
        checkUnlocked(keys("login", attempt));
    }

    /** Counts a failed login; returns whether this failure locked the login. */
    boolean recordFailure(LoginAttempt attempt) {
        return count(keys("login", attempt));
    }

    /** A successful login starts the count again. */
    void recordSuccess(LoginAttempt attempt) {
        List<String> keys = keys("login", attempt);
        ownTransaction.executeWithoutResult(status ->
                jdbc.update("delete from identity_login_throttle where key_hash in (?, ?)", keys.get(0), keys.get(1)));
    }

    /**
     * Counts a password reset request, which is limited like the login so it cannot be used to
     * flood an inbox.
     *
     * @throws ApiException {@code TOO_MANY_ATTEMPTS} when the limit is reached
     */
    void registerPasswordResetRequest(LoginAttempt attempt) {
        List<String> keys = keys("password-reset", attempt);
        checkUnlocked(keys);
        count(keys);
    }

    private void checkUnlocked(List<String> keys) {
        Instant now = clock.instant();
        List<OffsetDateTime> locks = jdbc.queryForList(
                "select locked_until from identity_login_throttle where key_hash in (?, ?) and locked_until > ?",
                OffsetDateTime.class, keys.get(0), keys.get(1), at(now));
        locks.stream()
                .map(OffsetDateTime::toInstant)
                .max(Instant::compareTo)
                .ifPresent(until -> {
                    throw tooManyAttempts(Duration.between(now, until));
                });
    }

    private boolean count(List<String> keys) {
        Instant now = clock.instant();
        OffsetDateTime windowStart = at(now.minus(properties.window()));
        OffsetDateTime lockedUntil = at(now.plus(properties.lockDuration()));
        Boolean locked = ownTransaction.execute(status -> {
            boolean any = false;
            for (String key : keys) {
                Integer failures = jdbc.queryForObject("""
                        insert into identity_login_throttle as t (key_hash, failures, window_start, locked_until)
                        values (?, 1, ?, null)
                        on conflict (key_hash) do update set
                            failures = case when t.window_start < ? or t.locked_until <= ? then 1 else t.failures + 1 end,
                            window_start = case when t.window_start < ? or t.locked_until <= ? then excluded.window_start
                                                else t.window_start end,
                            locked_until = case when t.locked_until <= ? then null else t.locked_until end
                        returning failures""",
                        Integer.class, key, at(now), windowStart, at(now), windowStart, at(now), at(now));
                if (failures != null && failures >= properties.maxFailures()) {
                    jdbc.update("update identity_login_throttle set locked_until = ? where key_hash = ? and locked_until is null",
                            lockedUntil, key);
                    any = true;
                }
            }
            return any;
        });
        return Boolean.TRUE.equals(locked);
    }

    private List<String> keys(String purpose, LoginAttempt attempt) {
        String email = attempt.email().strip().toLowerCase(Locale.ROOT);
        return List.of(
                hmac(purpose + ":address:" + email + "\n" + attempt.clientAddress()),
                hmac(purpose + ":account:" + email));
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
