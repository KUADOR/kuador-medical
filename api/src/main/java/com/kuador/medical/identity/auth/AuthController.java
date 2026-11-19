package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.AllowedWhilePasswordChangeRequired;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /auth/login} and {@code POST /auth/password}. */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final LoginService login;
    private final PasswordService passwords;
    private final CurrentUser currentUser;

    AuthController(LoginService login, PasswordService passwords, CurrentUser currentUser) {
        this.login = login;
        this.passwords = passwords;
        this.currentUser = currentUser;
    }

    @PublicEndpoint
    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return login.login(request.email(), request.password());
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
