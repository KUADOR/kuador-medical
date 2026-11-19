package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.AllowedWhilePasswordChangeRequired;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login across clinics, clinic and branch selection, refresh, logout, password reset, branch
 * switch and password change (contracts/user-registration.openapi.yaml, ADR-012 to ADR-014).
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthenticationService authentication;
    private final SessionIssuer sessions;
    private final PasswordResetService resets;
    private final PasswordService passwords;
    private final CurrentUser currentUser;

    AuthController(
            AuthenticationService authentication,
            SessionIssuer sessions,
            PasswordResetService resets,
            PasswordService passwords,
            CurrentUser currentUser) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.resets = resets;
        this.passwords = passwords;
        this.currentUser = currentUser;
    }

    @PublicEndpoint
    @PostMapping("/login")
    Object login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return LoginResponses.of(authentication.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    @PublicEndpoint
    @PostMapping("/login/clinic")
    Object selectClinic(@Valid @RequestBody AuthRequests.SelectClinicRequest request) {
        return LoginResponses.of(authentication.selectClinic(request.preAuthToken(), request.clinicId()));
    }

    @PublicEndpoint
    @PostMapping("/login/branch")
    Object selectBranch(@Valid @RequestBody AuthRequests.SelectBranchRequest request) {
        return LoginResponses.of(authentication.selectBranch(request.preAuthToken(), request.branchId()));
    }

    @PublicEndpoint
    @PostMapping("/refresh")
    LoginResponses.AuthenticatedResponse refresh(@Valid @RequestBody AuthRequests.RefreshRequest request) {
        return LoginResponses.authenticated(sessions.refresh(request.refreshToken()));
    }

    @PublicEndpoint
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody AuthRequests.RefreshRequest request) {
        sessions.logout(request.refreshToken());
    }

    @PublicEndpoint
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@Valid @RequestBody AuthRequests.PasswordResetRequest request, HttpServletRequest http) {
        authentication.requestPasswordReset(request.email(), http.getRemoteAddr());
    }

    @PublicEndpoint
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody AuthRequests.PasswordResetConfirmRequest request) {
        resets.confirm(request.token(), request.newPassword());
    }

    @AuthenticatedEndpoint
    @PostMapping("/switch-branch")
    LoginResponses.AuthenticatedResponse switchBranch(@Valid @RequestBody AuthRequests.SwitchBranchRequest request) {
        return LoginResponses.authenticated(sessions.switchBranch(
                currentUser.current().orElseThrow(ApiException::unauthenticated),
                request.branchId(),
                request.refreshToken()));
    }

    @AuthenticatedEndpoint
    @AllowedWhilePasswordChangeRequired
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwords.change(
                currentUser.current().orElseThrow(ApiException::unauthenticated),
                request.currentPassword(),
                request.newPassword());
    }
}
