package com.kuador.medical.identity.account;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invitations of clinic accounts (ADR-014): a one-time link valid for 72 hours (configurable)
 * that lets an invited account choose its password. Sending a new invitation invalidates the
 * previous ones. Emails leave after the transaction commits, so a rolled back creation sends
 * nothing; a failed delivery is logged without personal data and can be resent.
 */
@Service
@EnableConfigurationProperties({LinkProperties.class, OneTimeTokenProperties.class})
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final OneTimeTokens tokens;
    private final ClinicAccountRepository accounts;
    private final TenantDirectory tenants;
    private final NotificationSender notifications;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final LinkProperties links;
    private final OneTimeTokenProperties properties;

    InvitationService(
            OneTimeTokens tokens,
            ClinicAccountRepository accounts,
            TenantDirectory tenants,
            NotificationSender notifications,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            AuditRecorder audit,
            TransactionTemplate transaction,
            LinkProperties links,
            OneTimeTokenProperties properties) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.tenants = tenants;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.transaction = transaction;
        this.links = links;
        this.properties = properties;
    }

    /** What the invited person sees before choosing a password. */
    public record Preview(String clinicName, String maskedEmail, AccountKind kind, Instant expiresAt) {}

    /** An accepted invitation: the account is now active in its clinic. */
    public record Accepted(UUID tenantId, String clinicName, UUID accountId) {}

    /**
     * Invalidates the pending invitations of an invited account of the current clinic and emails
     * a new one. Must run in a transaction.
     *
     * @param actor who sends it, {@code null} when the system does (for example a patient registration)
     * @return expiry of the new invitation
     */
    public Instant send(ClinicAccount account, @Nullable UUID actor) {
        if (account.getStatus() != ClinicAccountStatus.INVITED) {
            throw ApiException.conflict(ErrorCode.ACCOUNT_ALREADY_ACTIVE, "Only an invited account can be invited.");
        }
        UUID tenantId = account.getTenantId() != null ? account.getTenantId() : currentTenant();
        String clinicName = tenants.find(tenantId).map(TenantDirectory.TenantInfo::name).orElseThrow();
        tokens.invalidatePending(account.getId(), OneTimeTokenPurpose.INVITATION);
        OneTimeTokens.IssuedToken token =
                tokens.issue(account.getId(), OneTimeTokenPurpose.INVITATION, properties.invitationTtl());
        EmailMessage email = InvitationEmail.compose(
                links, account.getEmail(), account.getFirstNames(), clinicName, token.value(), token.expiresAt());
        audit.record(AuditAction.INVITATION_SENT, tenantId, actor, AuditTarget.account(account.getId()));
        afterCommit(account.getId(), email);
        return token.expiresAt();
    }

    /** Expiry of the pending invitation of an account of the current clinic, if any. */
    public Optional<Instant> pendingExpiry(UUID accountId) {
        return tokens.pendingExpiry(accountId, OneTimeTokenPurpose.INVITATION);
    }

    /** Withdraws the pending invitations of an account of the current clinic (for example when it is disabled). */
    public void invalidate(UUID accountId) {
        tokens.invalidatePending(accountId, OneTimeTokenPurpose.INVITATION);
    }

    /** @throws ApiException {@code ONE_TIME_TOKEN_INVALID} for an unknown, used or expired invitation */
    public Preview preview(String token) {
        OneTimeTokens.ValidToken valid = validToken(token);
        return TenantContext.callAs(valid.tenantId(), () -> transaction.execute(status -> {
            String clinicName = activeClinicName(valid.tenantId());
            ClinicAccount account = invitedAccount(valid.userId());
            return new Preview(clinicName, EmailAddresses.mask(account.getEmail()), account.getKind(), valid.expiresAt());
        }));
    }

    /**
     * Sets the first password of the invited account and activates it, consuming the token. A
     * password that breaks the policy responds {@code WEAK_PASSWORD} and leaves the token valid.
     */
    public Accepted accept(String token, String password) {
        OneTimeTokens.ValidToken valid = validToken(token);
        passwordPolicy.checkFirstPassword(password);
        String passwordHash = passwordEncoder.encode(password);
        return TenantContext.callAs(valid.tenantId(), () -> transaction.execute(status -> {
            String clinicName = activeClinicName(valid.tenantId());
            ClinicAccount account = invitedAccount(valid.userId());
            if (!tokens.consume(valid.id())) {
                throw invalidToken();
            }
            account.acceptInvitation(passwordHash);
            accounts.saveAndFlush(account);
            audit.record(AuditAction.INVITATION_ACCEPTED, valid.tenantId(), account.getId(), AuditTarget.account(account.getId()));
            return new Accepted(valid.tenantId(), clinicName, account.getId());
        }));
    }

    private OneTimeTokens.ValidToken validToken(String token) {
        return tokens.findValid(token, OneTimeTokenPurpose.INVITATION).orElseThrow(InvitationService::invalidToken);
    }

    private String activeClinicName(UUID tenantId) {
        return tenants.find(tenantId)
                .filter(TenantDirectory.TenantInfo::active)
                .map(TenantDirectory.TenantInfo::name)
                .orElseThrow(InvitationService::invalidToken);
    }

    private ClinicAccount invitedAccount(UUID accountId) {
        return accounts.findInCurrentTenant(accountId)
                .filter(account -> account.getStatus() == ClinicAccountStatus.INVITED)
                .orElseThrow(InvitationService::invalidToken);
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
            // No email, token nor name in the log (Constitution V): the account id is enough to resend.
            log.warn("Invitation email of account {} could not be sent: {}", accountId, e.getClass().getSimpleName());
        }
    }

    private static UUID currentTenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }

    static ApiException invalidToken() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, ErrorCode.ONE_TIME_TOKEN_INVALID, "The invitation is not valid any more.");
    }
}
