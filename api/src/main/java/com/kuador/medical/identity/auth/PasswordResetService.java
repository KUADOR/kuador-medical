package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.TenantDirectory.TenantInfo;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.LinkProperties;
import com.kuador.medical.identity.account.OneTimeTokenProperties;
import com.kuador.medical.identity.account.OneTimeTokenPurpose;
import com.kuador.medical.identity.account.OneTimeTokens;
import com.kuador.medical.identity.account.PasswordPolicy;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password reset per clinic (ADR-014, spec story 3). Each link is valid only for the clinic
 * account it was sent for, once, until it expires; using it sets the new password and ends every
 * session of the account. Requests are started by {@link AuthenticationService}, the only one
 * that knows which clinics an email belongs to.
 *
 * <p>The email is sent in the background after the token is stored, so the response time does
 * not reveal whether the email has accounts, and a provider failure never changes the answer.
 */
@Service
class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final OneTimeTokens oneTimeTokens;
    private final OneTimeTokenProperties ttl;
    private final LinkProperties links;
    private final ClinicAccountRepository accounts;
    private final TenantDirectory tenants;
    private final NotificationSender notifications;
    private final PasswordPolicy policy;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokens refreshTokens;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;
    private final Executor background = Executors.newVirtualThreadPerTaskExecutor();

    PasswordResetService(
            OneTimeTokens oneTimeTokens,
            OneTimeTokenProperties ttl,
            LinkProperties links,
            ClinicAccountRepository accounts,
            TenantDirectory tenants,
            NotificationSender notifications,
            PasswordPolicy policy,
            PasswordEncoder passwordEncoder,
            RefreshTokens refreshTokens,
            AuditRecorder audit,
            TransactionTemplate transaction) {
        this.oneTimeTokens = oneTimeTokens;
        this.ttl = ttl;
        this.links = links;
        this.accounts = accounts;
        this.tenants = tenants;
        this.notifications = notifications;
        this.policy = policy;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.transaction = transaction;
    }

    /** Creates a reset link of the account for its clinic and emails it; does nothing if the account cannot log in. */
    void issue(UUID tenantId, UUID accountId) {
        Optional<EmailMessage> message = TenantContext.callAs(tenantId, () -> transaction.execute(status -> {
            Optional<TenantInfo> clinic = tenants.find(tenantId).filter(TenantInfo::active);
            Optional<ClinicAccount> account = accounts.findInCurrentTenant(accountId).filter(ClinicAccount::isActive);
            if (clinic.isEmpty() || account.isEmpty()) {
                return Optional.<EmailMessage>empty();
            }
            oneTimeTokens.invalidatePending(accountId, OneTimeTokenPurpose.PASSWORD_RESET);
            String token = oneTimeTokens.issue(accountId, OneTimeTokenPurpose.PASSWORD_RESET, ttl.passwordResetTtl()).value();
            audit.record(AuditAction.PASSWORD_RESET_REQUESTED, tenantId, null, AuditTarget.account(accountId));
            return Optional.of(email(account.get().getEmail(), clinic.get().name(),
                    links.withToken(links.passwordResetPath(), token)));
        }));
        message.ifPresent(email -> background.execute(() -> send(email)));
    }

    /**
     * Sets the new password of the account of the token and revokes its refresh tokens. A weak
     * password does not consume the token.
     *
     * @throws com.kuador.medical.shared.web.ApiException {@code ONE_TIME_TOKEN_INVALID} or {@code WEAK_PASSWORD}
     */
    void confirm(String token, String newPassword) {
        OneTimeTokens.ValidToken valid = oneTimeTokens.findValid(token, OneTimeTokenPurpose.PASSWORD_RESET)
                .orElseThrow(SessionErrors::oneTimeTokenInvalid);
        TenantContext.runAs(valid.tenantId(), () -> transaction.executeWithoutResult(status -> {
            if (tenants.find(valid.tenantId()).filter(TenantInfo::active).isEmpty()) {
                throw SessionErrors.oneTimeTokenInvalid();
            }
            ClinicAccount account = accounts.findInCurrentTenant(valid.userId())
                    .filter(ClinicAccount::isActive)
                    .orElseThrow(SessionErrors::oneTimeTokenInvalid);
            policy.checkNewPassword(newPassword, account.getPasswordHash());
            if (!oneTimeTokens.consume(valid.id())) {
                throw SessionErrors.oneTimeTokenInvalid();
            }
            account.changePassword(passwordEncoder.encode(newPassword));
            refreshTokens.revokeAllOf(account.getId());
            audit.record(AuditAction.PASSWORD_RESET_COMPLETED, valid.tenantId(), account.getId(),
                    AuditTarget.account(account.getId()));
        }));
    }

    private void send(EmailMessage email) {
        try {
            notifications.sendEmail(email);
        } catch (RuntimeException e) {
            // Only the type: the message could carry the address.
            log.warn("Password reset email could not be sent ({})", e.getClass().getName());
        }
    }

    private EmailMessage email(String to, String clinicName, String link) {
        String text = """
                Hola:

                Recibimos una solicitud para restablecer tu contraseña de %s en Medical Kuador.
                Abre este enlace para elegir una contraseña nueva. Vence en %s y solo se puede usar una vez:

                %s

                Si no la pediste, ignora este correo: tu contraseña no cambia.
                """.formatted(clinicName, readable(ttl.passwordResetTtl()), link);
        return new EmailMessage(to, "Restablece tu contraseña de " + clinicName, text);
    }

    private static String readable(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
