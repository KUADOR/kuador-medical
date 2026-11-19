package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email and password login (ADR-006). Every failure is the same {@code INVALID_CREDENTIALS}, and
 * an unknown email still pays for a hash check, so the answer does not reveal which part failed.
 */
@Service
class LoginService {

    private final UserAccountRepository accounts;
    private final ClinicCredentialLookup clinicAccounts;
    private final TenantDirectory tenants;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService tokens;
    private final AuditRecorder audit;
    private final String unknownAccountHash;

    LoginService(
            UserAccountRepository accounts,
            ClinicCredentialLookup clinicAccounts,
            TenantDirectory tenants,
            PasswordEncoder passwordEncoder,
            AccessTokenService tokens,
            AuditRecorder audit) {
        this.accounts = accounts;
        this.clinicAccounts = clinicAccounts;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.audit = audit;
        this.unknownAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        String normalized = EmailAddresses.normalize(email);
        Optional<UserAccount> platform = accounts.findByEmail(normalized)
                .filter(account -> account.isActive() && passwordEncoder.matches(password, account.getPasswordHash()));
        if (platform.isPresent()) {
            return platformLogin(platform.get());
        }
        // One account per clinic (ADR-012). Until MEDK-15 adds the clinic selection, the oldest
        // active clinic account whose password matches is used.
        List<ClinicCredentialLookup.Candidate> candidates = clinicAccounts.activeAccounts(normalized);
        if (candidates.isEmpty()) {
            passwordEncoder.matches(password, unknownAccountHash);
            throw invalidCredentials();
        }
        for (ClinicCredentialLookup.Candidate candidate : candidates) {
            if (passwordEncoder.matches(password, candidate.passwordHash()) && activeClinic(candidate.tenantId())) {
                audit.record(AuditAction.LOGIN_SUCCEEDED, candidate.tenantId(), candidate.accountId(), null);
                IssuedToken token = tokens.issue(candidate.accountId(), AccountScope.TENANT, candidate.tenantId());
                return new LoginResponse(token.value(), token.expiresInSeconds(), AccountScope.TENANT, false);
            }
        }
        throw invalidCredentials();
    }

    private LoginResponse platformLogin(UserAccount account) {
        audit.record(AuditAction.LOGIN_SUCCEEDED, null, account.getId(), null);
        IssuedToken token = tokens.issue(account.getId(), AccountScope.PLATFORM, null);
        return new LoginResponse(token.value(), token.expiresInSeconds(), AccountScope.PLATFORM, account.isMustChangePassword());
    }

    private boolean activeClinic(UUID tenantId) {
        return tenants.find(tenantId).filter(TenantDirectory.TenantInfo::active).isPresent();
    }

    static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
    }
}
