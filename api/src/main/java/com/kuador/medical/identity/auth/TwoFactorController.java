package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.identity.twofactor.TwoFactorService;
import com.kuador.medical.shared.security.AllowedWhenReadOnly;
import com.kuador.medical.shared.security.AllowedWhileTwoFactorPending;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /auth/two-factor/**} (MEDK-67, ADR-019): TOTP enrollment, its confirmation and the
 * verification after each login. They are the only authenticated operations a session with the
 * second factor pending can use; confirming or verifying returns new tokens that passed it.
 */
@RestController
@RequestMapping("/auth/two-factor")
@AuthenticatedEndpoint
@AllowedWhileTwoFactorPending
@AllowedWhenReadOnly
class TwoFactorController {

    private final TwoFactorService twoFactors;
    private final SessionIssuer sessions;
    private final CurrentUser currentUser;

    TwoFactorController(TwoFactorService twoFactors, SessionIssuer sessions, CurrentUser currentUser) {
        this.twoFactors = twoFactors;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    record CodeRequest(@NotNull @Pattern(regexp = "\\d{6}") String code) {

        @Override
        public String toString() {
            return "CodeRequest[redacted]";
        }
    }

    @PostMapping("/enrollment")
    TwoFactorService.Enrollment start() {
        return twoFactors.start(caller());
    }

    @PostMapping("/enrollment/confirm")
    LoginResponses.AuthenticatedResponse confirm(@Valid @RequestBody CodeRequest request) {
        AuthenticatedAccount caller = caller();
        UUID factor = twoFactors.confirm(caller, request.code());
        return LoginResponses.authenticated(sessions.twoFactorSession(caller, factor));
    }

    @PostMapping("/verify")
    LoginResponses.AuthenticatedResponse verify(@Valid @RequestBody CodeRequest request) {
        AuthenticatedAccount caller = caller();
        UUID factor = twoFactors.verify(caller, request.code());
        return LoginResponses.authenticated(sessions.twoFactorSession(caller, factor));
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
