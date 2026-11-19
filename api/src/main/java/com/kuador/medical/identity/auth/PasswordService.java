package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.PasswordPolicy;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Password change of the caller; clears {@code mustChangePassword} (FR-013) and is audited (FR-011). */
@Service
class PasswordService {

    private final UserAccountRepository accounts;
    private final ClinicAccountRepository clinicAccounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final AuditRecorder audit;

    PasswordService(
            UserAccountRepository accounts,
            ClinicAccountRepository clinicAccounts,
            PasswordEncoder passwordEncoder,
            PasswordPolicy policy,
            AuditRecorder audit) {
        this.accounts = accounts;
        this.clinicAccounts = clinicAccounts;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional
    public void change(AuthenticatedAccount caller, String currentPassword, String newPassword) {
        if (caller.scope() == AccountScope.TENANT) {
            changeClinicPassword(caller, currentPassword, newPassword);
            return;
        }
        UserAccount account = accounts.findById(caller.accountId()).orElseThrow(SessionErrors::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw SessionErrors.invalidCredentials();
        }
        policy.checkNewPassword(newPassword, account.getPasswordHash());
        account.changePassword(passwordEncoder.encode(newPassword));
        audit.record(AuditAction.PASSWORD_CHANGED, caller.tenantId(), account.getId(), AuditTarget.account(account.getId()));
    }

    private void changeClinicPassword(AuthenticatedAccount caller, String currentPassword, String newPassword) {
        ClinicAccount account = clinicAccounts.findInCurrentTenant(caller.accountId())
                .filter(found -> found.getPasswordHash() != null)
                .orElseThrow(SessionErrors::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw SessionErrors.invalidCredentials();
        }
        policy.checkNewPassword(newPassword, account.getPasswordHash());
        account.changePassword(passwordEncoder.encode(newPassword));
        audit.record(AuditAction.PASSWORD_CHANGED, caller.tenantId(), account.getId(), AuditTarget.account(account.getId()));
    }
}
