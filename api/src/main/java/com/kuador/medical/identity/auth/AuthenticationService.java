package com.kuador.medical.identity.auth;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.TenantDirectory.TenantInfo;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.shared.web.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login across clinics (ADR-012, FR-004, FR-006) and the start of the password reset. It is the
 * only user of {@link CrossTenantCredentialLookup}: it verifies the password in every clinic of
 * the email and reveals only the clinics where it matched. Unknown email, wrong password and an
 * account that cannot log in fail the same way, an unknown email still pays for a hash check,
 * and the attempts are limited per email + address and per email.
 */
@Service
class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final CrossTenantCredentialLookup lookup;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle throttle;
    private final TenantDirectory tenants;
    private final SessionIssuer sessions;
    private final PreAuthTokens preAuthTokens;
    private final PasswordResetService resets;
    private final AuditRecorder audit;
    private final String unknownAccountHash;

    AuthenticationService(
            CrossTenantCredentialLookup lookup,
            PasswordEncoder passwordEncoder,
            LoginThrottle throttle,
            TenantDirectory tenants,
            SessionIssuer sessions,
            PreAuthTokens preAuthTokens,
            PasswordResetService resets,
            AuditRecorder audit) {
        this.lookup = lookup;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.tenants = tenants;
        this.sessions = sessions;
        this.preAuthTokens = preAuthTokens;
        this.resets = resets;
        this.audit = audit;
        this.unknownAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * @throws ApiException {@code INVALID_CREDENTIALS} or {@code TOO_MANY_ATTEMPTS}
     */
    LoginResult login(String email, String password, String clientAddress) {
        LoginAttempt attempt = new LoginAttempt(EmailAddresses.normalize(email), clientAddress);
        throttle.checkAllowed(attempt);
        List<StoredCredential> credentials = lookup.activeCredentials(attempt.email());
        if (credentials.isEmpty()) {
            passwordEncoder.matches(password, unknownAccountHash);
            throw failed(attempt);
        }
        Map<String, Boolean> checked = new HashMap<>();
        List<StoredCredential> matched = credentials.stream()
                .filter(credential -> checked.computeIfAbsent(
                        credential.passwordHash(), hash -> passwordEncoder.matches(password, hash)))
                .toList();
        Optional<StoredCredential> platform = matched.stream().filter(StoredCredential::platformAdmin).findFirst();
        if (platform.isPresent()) {
            throttle.recordSuccess(attempt);
            return sessions.startPlatformSession(platform.get().accountId());
        }
        Map<UUID, LoginResult.Summary> clinics = new LinkedHashMap<>();
        List<PreAuthCandidate> candidates = new ArrayList<>();
        for (StoredCredential credential : matched) {
            UUID tenantId = credential.tenantId();
            if (tenantId == null || clinics.containsKey(tenantId)) {
                continue;
            }
            tenants.find(tenantId).filter(TenantInfo::active).ifPresent(clinic -> {
                clinics.put(tenantId, new LoginResult.Summary(clinic.id(), clinic.name()));
                candidates.add(new PreAuthCandidate(tenantId, credential.accountId()));
            });
        }
        if (candidates.isEmpty()) {
            throw failed(attempt);
        }
        throttle.recordSuccess(attempt);
        if (candidates.size() == 1) {
            return sessions.startTenantSession(candidates.getFirst().tenantId(), candidates.getFirst().accountId());
        }
        return new LoginResult.ClinicSelection(preAuthTokens.issue(candidates), List.copyOf(clinics.values()));
    }

    /**
     * Completes a clinic selection with one of the listed clinics; the pre-auth token is used up.
     *
     * @throws ApiException {@code INVALID_SESSION_TOKEN}
     */
    LoginResult selectClinic(String preAuthToken, UUID clinicId) {
        PreAuthClaims claims = preAuthTokens.find(preAuthToken).orElseThrow(SessionErrors::invalidSessionToken);
        PreAuthCandidate chosen = claims.candidates().stream()
                .filter(candidate -> candidate.tenantId().equals(clinicId))
                .findFirst()
                .orElseThrow(SessionErrors::invalidSessionToken);
        preAuthTokens.consume(claims, chosen);
        return sessions.startTenantSession(chosen.tenantId(), chosen.accountId());
    }

    /**
     * Completes a branch selection; the pre-auth token must name a single clinic and is used up.
     *
     * @throws ApiException {@code INVALID_SESSION_TOKEN} or {@code BRANCH_NOT_ASSIGNED}
     */
    LoginResult selectBranch(String preAuthToken, UUID branchId) {
        PreAuthClaims claims = preAuthTokens.find(preAuthToken).orElseThrow(SessionErrors::invalidSessionToken);
        if (claims.candidates().size() != 1) {
            throw SessionErrors.invalidSessionToken();
        }
        PreAuthCandidate chosen = claims.candidates().getFirst();
        sessions.requireAssignedBranch(chosen.tenantId(), chosen.accountId(), branchId);
        preAuthTokens.consume(claims, chosen);
        return sessions.startBranchSession(chosen.tenantId(), chosen.accountId(), branchId);
    }

    /**
     * Sends one reset link per clinic of the email. Answers the same whether or not the email
     * exists: nothing is revealed, and a failure for one clinic does not change the answer.
     *
     * @throws ApiException {@code TOO_MANY_ATTEMPTS} when the email or address asks too often
     */
    void requestPasswordReset(String email, String clientAddress) {
        LoginAttempt attempt = new LoginAttempt(EmailAddresses.normalize(email), clientAddress);
        throttle.registerPasswordResetRequest(attempt);
        for (StoredCredential credential : lookup.activeCredentials(attempt.email())) {
            if (credential.platformAdmin() || credential.tenantId() == null) {
                continue;
            }
            try {
                resets.issue(credential.tenantId(), credential.accountId());
            } catch (RuntimeException e) {
                // Only the type: the message could carry personal data.
                log.error("Password reset link could not be issued ({})", e.getClass().getName());
            }
        }
    }

    private ApiException failed(LoginAttempt attempt) {
        boolean locked = throttle.recordFailure(attempt);
        audit.record(AuditAction.LOGIN_FAILED, null, null, null);
        if (locked) {
            audit.record(AuditAction.LOGIN_LOCKED, null, null, null);
        }
        return SessionErrors.invalidCredentials();
    }
}
