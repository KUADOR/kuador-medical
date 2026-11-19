package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.TenantDirectory.TenantInfo;
import com.kuador.medical.identity.account.PasswordPolicy;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.identity.membership.MembershipStatus;
import com.kuador.medical.identity.membership.TenantMembershipRepository;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password reset per clinic (ADR-014, spec story 3). Each link is valid only for the clinic it
 * was sent for, once, until it expires; using it sets the new password and ends every session
 * of the account. Requests are started by {@link AuthenticationService}, the only one that knows
 * which clinics an email belongs to.
 */
@Service
class PasswordResetService {

    private final OneTimeTokens oneTimeTokens;
    private final OneTimeTokenProperties ttl;
    private final LinkProperties links;
    private final UserAccountRepository accounts;
    private final TenantMembershipRepository memberships;
    private final TenantDirectory tenants;
    private final NotificationSender notifications;
    private final PasswordPolicy policy;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokens refreshTokens;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;

    PasswordResetService(
            OneTimeTokens oneTimeTokens,
            OneTimeTokenProperties ttl,
            LinkProperties links,
            UserAccountRepository accounts,
            TenantMembershipRepository memberships,
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
        this.memberships = memberships;
        this.tenants = tenants;
        this.notifications = notifications;
        this.policy = policy;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.transaction = transaction;
    }

    /** Creates a reset link of the account for that clinic and emails it; does nothing if the account cannot log in there. */
    void issue(UUID tenantId, UUID accountId) {
        Optional<EmailMessage> message = TenantContext.callAs(tenantId, () -> transaction.execute(status -> {
            Optional<TenantInfo> clinic = tenants.find(tenantId).filter(TenantInfo::active);
            Optional<UserAccount> account = activeMember(accountId);
            if (clinic.isEmpty() || account.isEmpty()) {
                return Optional.<EmailMessage>empty();
            }
            String token = oneTimeTokens.issue(tenantId, accountId, OneTimeTokens.Purpose.PASSWORD_RESET, ttl.passwordResetTtl());
            audit.record(AuditAction.PASSWORD_RESET_REQUESTED, tenantId, null, AuditTarget.account(accountId));
            return Optional.of(email(account.get().getEmail(), clinic.get().name(), links.passwordResetLink(token)));
        }));
        message.ifPresent(notifications::sendEmail);
    }

    /**
     * Sets the new password of the account of the token and revokes its refresh tokens. A weak
     * password does not consume the token.
     *
     * @throws com.kuador.medical.shared.web.ApiException {@code ONE_TIME_TOKEN_INVALID} or {@code WEAK_PASSWORD}
     */
    void confirm(String token, String newPassword) {
        OneTimeTokens.Valid valid = oneTimeTokens.findValid(token, OneTimeTokens.Purpose.PASSWORD_RESET)
                .orElseThrow(SessionErrors::oneTimeTokenInvalid);
        TenantContext.runAs(valid.tenantId(), () -> transaction.executeWithoutResult(status -> {
            if (tenants.find(valid.tenantId()).filter(TenantInfo::active).isEmpty()) {
                throw SessionErrors.oneTimeTokenInvalid();
            }
            UserAccount account = activeMember(valid.userId()).orElseThrow(SessionErrors::oneTimeTokenInvalid);
            policy.checkNewPassword(newPassword, account.getPasswordHash());
            if (!oneTimeTokens.markUsed(valid.id())) {
                throw SessionErrors.oneTimeTokenInvalid();
            }
            account.changePassword(passwordEncoder.encode(newPassword));
            refreshTokens.revokeAllOf(account.getId());
            audit.record(AuditAction.PASSWORD_RESET_COMPLETED, valid.tenantId(), account.getId(),
                    AuditTarget.account(account.getId()));
        }));
    }

    /** The account, if active and an active member of the bound clinic. */
    private Optional<UserAccount> activeMember(UUID accountId) {
        return accounts.findById(accountId)
                .filter(UserAccount::isActive)
                .filter(account -> !account.isPlatformAdmin())
                .filter(account -> memberships.findByUserAccountIdAndStatus(accountId, MembershipStatus.ACTIVE).isPresent());
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
