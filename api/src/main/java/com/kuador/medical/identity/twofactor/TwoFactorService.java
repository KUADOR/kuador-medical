package com.kuador.medical.identity.twofactor;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.TwoFactorStep;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.twofactor.TwoFactorStore.Factor;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.TooManyAttemptsException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TOTP second factor of clinic accounts (ADR-019): enrollment with a secret shown once and stored
 * encrypted, confirmation, verification after each login, and reset by a SUPER_ADMIN (plan D7).
 * A code is accepted once; repeated wrong codes lock the factor for a while (429). Wrong codes
 * are counted in a transaction that commits before the error is answered. Neither the secret nor
 * the codes reach the logs or the audit trail.
 */
@Service
@EnableConfigurationProperties(TwoFactorProperties.class)
public class TwoFactorService {

    /** What the enrollment returns: the secret is shown only here. */
    public record Enrollment(String secret, String otpauthUri, String issuer, int digits, int period) {

        @Override
        public String toString() {
            return "Enrollment[redacted]";
        }
    }

    private enum Outcome { ACCEPTED, WRONG, LOCKED, NOW_LOCKED }

    private record Check(Outcome outcome, UUID factorId, long step, Duration retryAfter) {}

    private final TwoFactorStore store;
    private final SecretCipher cipher;
    private final ClinicAccountRepository accounts;
    private final AuditRecorder audit;
    private final TwoFactorProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    TwoFactorService(
            TwoFactorStore store,
            SecretCipher cipher,
            ClinicAccountRepository accounts,
            AuditRecorder audit,
            TwoFactorProperties properties,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.cipher = cipher;
        this.accounts = accounts;
        this.audit = audit;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
    }

    /**
     * {@code POST /auth/two-factor/enrollment}: a new secret for the caller, replacing an
     * unconfirmed one.
     *
     * @throws ApiException {@code TWO_FACTOR_ALREADY_ENABLED}
     */
    public Enrollment start(AuthenticatedAccount caller) {
        requireClinicAccount(caller);
        return Objects.requireNonNull(transaction.execute(status -> {
            ClinicAccount account = accounts.findInCurrentTenant(caller.accountId()).orElseThrow(ApiException::unauthenticated);
            if (store.currentForUpdate(caller.accountId()).filter(Factor::enabled).isPresent()) {
                throw alreadyEnabled();
            }
            byte[] secret = Totp.newSecret();
            store.startEnrollment(caller.accountId(), cipher.encrypt(secret), clock.instant());
            String encoded = Base32.encode(secret);
            return new Enrollment(encoded, otpauthUri(EmailAddresses.mask(account.getEmail()), encoded),
                    properties.issuer(), Totp.DIGITS, Totp.PERIOD_SECONDS);
        }));
    }

    /**
     * {@code POST /auth/two-factor/enrollment/confirm}: enables the pending secret when the code
     * matches.
     *
     * @return id of the enabled factor, verified by the session
     * @throws ApiException {@code TWO_FACTOR_ALREADY_ENABLED} (nothing pending), {@code TWO_FACTOR_CODE_INVALID}
     *         or {@code TOO_MANY_ATTEMPTS}
     */
    public UUID confirm(AuthenticatedAccount caller, String code) {
        requireClinicAccount(caller);
        Check check = Objects.requireNonNull(transaction.execute(status -> {
            Factor factor = store.currentForUpdate(caller.accountId())
                    .filter(current -> !current.enabled())
                    .orElseThrow(TwoFactorService::alreadyEnabled);
            Check result = check(factor, code);
            if (result.outcome() == Outcome.ACCEPTED) {
                store.enable(factor.id(), result.step(), clock.instant());
                audit.record(AuditAction.TWO_FACTOR_ENABLED, caller.tenantId(), caller.accountId(),
                        AuditTarget.account(caller.accountId()));
            }
            return result;
        }));
        return accepted(check, caller);
    }

    /**
     * {@code POST /auth/two-factor/verify}: the code of the enabled factor, after a login.
     *
     * @return id of the verified factor
     * @throws ApiException {@code TWO_FACTOR_REQUIRED} (nothing enabled: enroll first),
     *         {@code TWO_FACTOR_CODE_INVALID} or {@code TOO_MANY_ATTEMPTS}
     */
    public UUID verify(AuthenticatedAccount caller, String code) {
        requireClinicAccount(caller);
        Check check = Objects.requireNonNull(transaction.execute(status -> {
            Factor factor = store.currentForUpdate(caller.accountId())
                    .filter(Factor::enabled)
                    .orElseThrow(() -> ApiException.twoFactorRequired(TwoFactorStep.ENROLLMENT_REQUIRED.name()));
            Check result = check(factor, code);
            if (result.outcome() == Outcome.ACCEPTED) {
                store.markUsed(factor.id(), result.step());
            }
            return result;
        }));
        return accepted(check, caller);
    }

    /**
     * Ends the second factor of an account of the current clinic (plan D7): it enrolls again on
     * its next request. Must run in the caller's transaction, with the clinic bound.
     *
     * @return whether the account had a factor
     */
    public boolean reset(UUID accountId, UUID actor, String reason) {
        boolean had = store.reset(accountId, actor, reason.strip(), clock.instant());
        audit.record(AuditAction.TWO_FACTOR_RESET, currentTenant(accountId), actor, AuditTarget.account(accountId));
        return had;
    }

    /** Whether the account of the current clinic has its second factor enabled. */
    public boolean isEnabled(UUID accountId) {
        return store.enabledFactorId(accountId).isPresent();
    }

    /** Checks a code against a factor locked for update; on success {@code step} is the matched step. */
    private Check check(Factor factor, String code) {
        Instant now = clock.instant();
        if (factor.lockedUntil() != null && factor.lockedUntil().isAfter(now)) {
            return new Check(Outcome.LOCKED, factor.id(), -1, Duration.between(now, factor.lockedUntil()));
        }
        byte[] secret = cipher.decrypt(factor.secretEncrypted());
        OptionalLong step = Totp.match(secret, code, now, factor.lastUsedStep());
        if (step.isPresent()) {
            return new Check(Outcome.ACCEPTED, factor.id(), step.getAsLong(), Duration.ZERO);
        }
        int failures = store.recordFailure(factor.id());
        if (failures >= properties.maxFailures()) {
            store.lock(factor.id(), now.plus(properties.lockDuration()));
            return new Check(Outcome.NOW_LOCKED, factor.id(), -1, properties.lockDuration());
        }
        return new Check(Outcome.WRONG, factor.id(), -1, Duration.ZERO);
    }

    private UUID accepted(Check check, AuthenticatedAccount caller) {
        switch (check.outcome()) {
            case ACCEPTED -> {
                return check.factorId();
            }
            case WRONG -> throw codeInvalid();
            case NOW_LOCKED -> {
                audit.record(AuditAction.TWO_FACTOR_LOCKED, caller.tenantId(), caller.accountId(),
                        AuditTarget.account(caller.accountId()));
                throw new TooManyAttemptsException(check.retryAfter());
            }
            case LOCKED -> throw new TooManyAttemptsException(check.retryAfter());
        }
        throw new IllegalStateException("Unknown outcome");
    }

    private String otpauthUri(String label, String secret) {
        String issuer = encode(properties.issuer());
        return "otpauth://totp/" + issuer + ":" + encode(label)
                + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=" + Totp.ALGORITHM + "&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void requireClinicAccount(AuthenticatedAccount caller) {
        if (caller.scope() != AccountScope.TENANT || caller.tenantId() == null) {
            throw ApiException.forbidden("The second factor applies to clinic accounts.");
        }
    }

    private static UUID currentTenant(UUID accountId) {
        return com.kuador.medical.shared.tenancy.TenantContext.current()
                .orElseThrow(() -> new IllegalStateException("No clinic is bound for account " + accountId));
    }

    private static ApiException alreadyEnabled() {
        return ApiException.conflict(ErrorCode.TWO_FACTOR_ALREADY_ENABLED, "There is no pending enrollment.");
    }

    private static ApiException codeInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.TWO_FACTOR_CODE_INVALID, "The code is not valid.");
    }
}
