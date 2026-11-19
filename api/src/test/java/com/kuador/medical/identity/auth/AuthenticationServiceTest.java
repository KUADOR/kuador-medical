package com.kuador.medical.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.TenantDirectory.TenantInfo;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Login across clinics (ADR-012, FR-004, FR-006): only the clinics where the password matched
 * are revealed, every failure looks the same, and the pre-auth token only opens a listed clinic.
 */
class AuthenticationServiceTest {

    private static final String EMAIL = "jane.doe@example.test";
    private static final String PASSWORD = "pw-" + UUID.randomUUID();
    private static final String IP = "203.0.113.10";
    private static final PasswordEncoder ENCODER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);
    private static final String OTHER_HASH = ENCODER.encode("pw-" + UUID.randomUUID());

    private final UUID clinicA = UUID.randomUUID();
    private final UUID clinicB = UUID.randomUUID();
    private final UUID clinicC = UUID.randomUUID();
    private final Map<UUID, TenantInfo> clinics = Map.of(
            clinicA, new TenantInfo(clinicA, "Clinic A", true),
            clinicB, new TenantInfo(clinicB, "Clinic B", true),
            clinicC, new TenantInfo(clinicC, "Clinic C", true));

    private final CrossTenantCredentialLookup lookup = mock(CrossTenantCredentialLookup.class);
    private final LoginThrottle throttle = mock(LoginThrottle.class);
    private final TenantDirectory tenants = mock(TenantDirectory.class);
    private final SessionIssuer sessions = mock(SessionIssuer.class);
    private final PreAuthTokens preAuthTokens = mock(PreAuthTokens.class);
    private final PasswordResetService resets = mock(PasswordResetService.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final IssuedToken preAuth = new IssuedToken("pre-auth-" + UUID.randomUUID(), 300);

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        when(tenants.find(any())).thenAnswer(call -> Optional.ofNullable(clinics.get(call.<UUID>getArgument(0))));
        service = new AuthenticationService(
                lookup, ENCODER, throttle, tenants, sessions, preAuthTokens, resets, audit);
        when(preAuthTokens.issue(any())).thenReturn(preAuth);
    }

    @Test
    void onlyTheClinicsWhereThePasswordMatchedAreListed() {
        UUID accountA = UUID.randomUUID();
        UUID accountC = UUID.randomUUID();
        when(lookup.activeCredentials(EMAIL)).thenReturn(List.of(
                clinicCredential(accountA, clinicA, PASSWORD_HASH),
                clinicCredential(UUID.randomUUID(), clinicB, OTHER_HASH),
                clinicCredential(accountC, clinicC, PASSWORD_HASH)));

        LoginResult result = service.login(EMAIL, PASSWORD, IP);

        assertThat(result).isInstanceOf(LoginResult.ClinicSelection.class);
        var selection = (LoginResult.ClinicSelection) result;
        assertThat(selection.clinics()).extracting(LoginResult.Summary::id).containsExactly(clinicA, clinicC);
        assertThat(selection.preAuthToken()).isEqualTo(preAuth.value());
        verify(preAuthTokens).issue(List.of(
                new PreAuthCandidate(clinicA, accountA), new PreAuthCandidate(clinicC, accountC)));
        verify(throttle).recordSuccess(new LoginAttempt(EMAIL, IP));
        verifyNoInteractions(sessions);
    }

    @Test
    void aSingleMatchingClinicStartsItsSessionDirectly() {
        UUID accountA = UUID.randomUUID();
        when(lookup.activeCredentials(EMAIL)).thenReturn(List.of(
                clinicCredential(accountA, clinicA, PASSWORD_HASH),
                clinicCredential(UUID.randomUUID(), clinicB, OTHER_HASH)));
        LoginResult session = mock(LoginResult.class);
        when(sessions.startTenantSession(clinicA, accountA)).thenReturn(session);

        assertThat(service.login(EMAIL, PASSWORD, IP)).isSameAs(session);
    }

    @Test
    void aSuspendedClinicIsNeverListed() {
        UUID accountA = UUID.randomUUID();
        when(tenants.find(clinicB)).thenReturn(Optional.of(new TenantInfo(clinicB, "Clinic B", false)));
        when(lookup.activeCredentials(EMAIL)).thenReturn(List.of(
                clinicCredential(accountA, clinicA, PASSWORD_HASH),
                clinicCredential(UUID.randomUUID(), clinicB, PASSWORD_HASH)));
        LoginResult session = mock(LoginResult.class);
        when(sessions.startTenantSession(clinicA, accountA)).thenReturn(session);

        assertThat(service.login(EMAIL, PASSWORD, IP)).isSameAs(session);
    }

    @Test
    void unknownEmailAndWrongPasswordFailTheSameWay() {
        when(lookup.activeCredentials(EMAIL)).thenReturn(List.of(clinicCredential(UUID.randomUUID(), clinicA, OTHER_HASH)));
        when(lookup.activeCredentials("nobody@example.test")).thenReturn(List.of());

        ApiException wrongPassword = catchApiException(() -> service.login(EMAIL, PASSWORD, IP));
        ApiException unknownEmail = catchApiException(() -> service.login("nobody@example.test", PASSWORD, IP));

        for (ApiException failure : List.of(wrongPassword, unknownEmail)) {
            assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
        assertThat(wrongPassword.status()).isEqualTo(unknownEmail.status());
        verify(throttle, times(2)).recordFailure(any());
        verifyNoInteractions(sessions);
    }

    @Test
    void aLockedAttemptIsRejectedBeforeAnyPasswordIsChecked() {
        doThrow(LoginThrottle.tooManyAttempts(Duration.ofMinutes(10))).when(throttle).checkAllowed(any());

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, IP))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS));
        verifyNoInteractions(lookup, sessions);
    }

    @Test
    void thePlatformAccountGetsAPlatformSession() {
        UUID admin = UUID.randomUUID();
        when(lookup.activeCredentials(EMAIL))
                .thenReturn(List.of(new StoredCredential(admin, null, PASSWORD_HASH, true)));
        LoginResult session = mock(LoginResult.class);
        when(sessions.startPlatformSession(admin)).thenReturn(session);

        assertThat(service.login(EMAIL, PASSWORD, IP)).isSameAs(session);
    }

    @Test
    void choosingAClinicThatWasNotListedIsRejected() {
        UUID accountA = UUID.randomUUID();
        when(preAuthTokens.find("listed")).thenReturn(Optional.of(
                new PreAuthClaims(UUID.randomUUID(), List.of(new PreAuthCandidate(clinicA, accountA)))));

        assertThatThrownBy(() -> service.selectClinic("listed", clinicB))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN));
        verify(preAuthTokens, never()).consume(any(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void choosingAListedClinicConsumesThePreAuthTokenAndStartsTheSession() {
        UUID accountA = UUID.randomUUID();
        PreAuthClaims claims = new PreAuthClaims(UUID.randomUUID(), List.of(
                new PreAuthCandidate(clinicA, accountA), new PreAuthCandidate(clinicC, UUID.randomUUID())));
        when(preAuthTokens.find("listed")).thenReturn(Optional.of(claims));
        LoginResult session = mock(LoginResult.class);
        when(sessions.startTenantSession(clinicA, accountA)).thenReturn(session);

        assertThat(service.selectClinic("listed", clinicA)).isSameAs(session);
        verify(preAuthTokens).consume(claims, new PreAuthCandidate(clinicA, accountA));
    }

    @Test
    void anUnknownOrUsedPreAuthTokenIsRejected() {
        when(preAuthTokens.find(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.selectClinic("used", clinicA))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN));
        assertThatThrownBy(() -> service.selectBranch("used", UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN));
        verifyNoInteractions(sessions);
    }

    @Test
    void aClinicSelectionTokenCannotChooseABranch() {
        when(preAuthTokens.find("clinics")).thenReturn(Optional.of(new PreAuthClaims(UUID.randomUUID(), List.of(
                new PreAuthCandidate(clinicA, UUID.randomUUID()), new PreAuthCandidate(clinicC, UUID.randomUUID())))));

        assertThatThrownBy(() -> service.selectBranch("clinics", UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN));
        verifyNoInteractions(sessions);
    }

    private static StoredCredential clinicCredential(UUID accountId, UUID tenantId, String hash) {
        return new StoredCredential(accountId, tenantId, hash, false);
    }

    private static ApiException catchApiException(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Expected an ApiException");
    }
}
