package com.kuador.medical.identity.account;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Email verification of self-registered patients (ADR-014): a one-time link valid for 24 hours
 * (configurable) that activates an account {@code PENDING_EMAIL_VERIFICATION}. Emails leave after
 * the transaction commits; a failed delivery is logged without personal data.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final OneTimeTokens tokens;
    private final ClinicAccountRepository accounts;
    private final TenantDirectory tenants;
    private final NotificationSender notifications;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final LinkProperties links;
    private final OneTimeTokenProperties properties;

    EmailVerificationService(
            OneTimeTokens tokens,
            ClinicAccountRepository accounts,
            TenantDirectory tenants,
            NotificationSender notifications,
            AuditRecorder audit,
            TransactionTemplate transaction,
            LinkProperties links,
            OneTimeTokenProperties properties) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.tenants = tenants;
        this.notifications = notifications;
        this.audit = audit;
        this.transaction = transaction;
        this.links = links;
        this.properties = properties;
    }

    /** Invalidates the pending verifications of the account of the current clinic and emails a new one. */
    public void send(ClinicAccount account) {
        if (account.getStatus() != ClinicAccountStatus.PENDING_EMAIL_VERIFICATION) {
            throw new IllegalStateException("Only an account pending verification is sent a verification");
        }
        UUID tenantId = account.getTenantId() != null
                ? account.getTenantId()
                : TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
        String clinicName = tenants.find(tenantId).map(TenantDirectory.TenantInfo::name).orElseThrow();
        tokens.invalidatePending(account.getId(), OneTimeTokenPurpose.EMAIL_VERIFICATION);
        OneTimeTokens.IssuedToken token = tokens.issue(
                account.getId(), OneTimeTokenPurpose.EMAIL_VERIFICATION, properties.emailVerificationTtl());
        EmailMessage email = EmailVerificationEmail.compose(
                links, account.getEmail(), account.getFirstNames(), clinicName, token.value(), token.expiresAt());
        audit.record(AuditAction.EMAIL_VERIFICATION_SENT, tenantId, null, AuditTarget.account(account.getId()));
        afterCommit(account.getId(), email);
    }

    /**
     * Activates the account of the token and consumes it.
     *
     * @throws ApiException {@code ONE_TIME_TOKEN_INVALID} for an unknown, used or expired token
     */
    public void confirm(String token) {
        OneTimeTokens.ValidToken valid = tokens.findValid(token, OneTimeTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(EmailVerificationService::invalidToken);
        TenantContext.runAs(valid.tenantId(), () -> transaction.executeWithoutResult(status -> {
            boolean activeClinic = tenants.find(valid.tenantId()).map(TenantDirectory.TenantInfo::active).orElse(false);
            ClinicAccount account = accounts.findInCurrentTenant(valid.userId())
                    .filter(found -> found.getStatus() == ClinicAccountStatus.PENDING_EMAIL_VERIFICATION)
                    .filter(found -> activeClinic)
                    .orElseThrow(EmailVerificationService::invalidToken);
            if (!tokens.consume(valid.id())) {
                throw invalidToken();
            }
            account.verifyEmail();
            accounts.saveAndFlush(account);
            audit.record(AuditAction.EMAIL_VERIFIED, valid.tenantId(), account.getId(), AuditTarget.account(account.getId()));
        }));
    }

    private void afterCommit(UUID accountId, EmailMessage email) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(accountId, email);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(accountId, email);
            }
        });
    }

    private void deliver(UUID accountId, EmailMessage email) {
        try {
            notifications.sendEmail(email);
        } catch (RuntimeException e) {
            // No email, token nor name in the log (Constitution V).
            log.warn("Verification email of account {} could not be sent: {}", accountId, e.getClass().getSimpleName());
        }
    }

    static ApiException invalidToken() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, ErrorCode.ONE_TIME_TOKEN_INVALID, "The verification link is not valid any more.");
    }
}
