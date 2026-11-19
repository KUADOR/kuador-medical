package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.account.EmailVerificationService;
import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /auth/email-verification/confirm} (ADR-014): activates a self-registered patient.
 * The token comes in the body, never in the path or the query.
 */
@RestController
@RequestMapping("/auth/email-verification")
class EmailVerificationController {

    private final EmailVerificationService verifications;

    EmailVerificationController(EmailVerificationService verifications) {
        this.verifications = verifications;
    }

    record ConfirmRequest(@NotBlank @Size(min = 16, max = 512) String token) {

        @Override
        public String toString() {
            return "ConfirmRequest[redacted]";
        }
    }

    @PublicEndpoint
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@Valid @RequestBody ConfirmRequest request) {
        verifications.confirm(request.token());
    }
}
