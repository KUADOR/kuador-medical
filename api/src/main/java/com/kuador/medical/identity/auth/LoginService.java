package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.identity.membership.MembershipLookup;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
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
    private final MembershipLookup memberships;
    private final TenantDirectory tenants;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService tokens;
    private final AuditRecorder audit;
    private final String unknownAccountHash;

    LoginService(
            UserAccountRepository accounts,
            MembershipLookup memberships,
            TenantDirectory tenants,
            PasswordEncoder passwordEncoder,
            AccessTokenService tokens,
            AuditRecorder audit) {
        this.accounts = accounts;
        this.memberships = memberships;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.audit = audit;
        this.unknownAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        Optional<UserAccount> found = accounts.findByEmail(EmailAddresses.normalize(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, unknownAccountHash);
            throw invalidCredentials();
        }
        UserAccount account = found.get();
        if (!passwordEncoder.matches(password, account.getPasswordHash()) || !account.isActive()) {
            throw invalidCredentials();
        }
        AccountScope scope = account.isPlatformAdmin() ? AccountScope.PLATFORM : AccountScope.TENANT;
        UUID tenantId = scope == AccountScope.PLATFORM ? null : activeTenantOf(account);
        audit.record(AuditAction.LOGIN_SUCCEEDED, tenantId, account.getId(), null);
        IssuedToken token = tokens.issue(account.getId(), scope, tenantId);
        return new LoginResponse(token.value(), token.expiresInSeconds(), scope, account.isMustChangePassword());
    }

    private UUID activeTenantOf(UserAccount account) {
        return memberships.activeTenantOf(account.getId())
                .filter(tenantId -> tenants.find(tenantId).filter(TenantDirectory.TenantInfo::active).isPresent())
                .orElseThrow(LoginService::invalidCredentials);
    }

    static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
    }
}
