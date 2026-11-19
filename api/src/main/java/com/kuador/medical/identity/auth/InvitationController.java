package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.account.InvitationService;
import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /auth/invitations/preview} and {@code /accept} (ADR-014): the token comes in the
 * body, never in the path or the query, so it never reaches an access log.
 */
@RestController
@RequestMapping("/auth/invitations")
class InvitationController {

    private final InvitationService invitations;
    private final SessionIssuer sessions;

    InvitationController(InvitationService invitations, SessionIssuer sessions) {
        this.invitations = invitations;
        this.sessions = sessions;
    }

    record TokenRequest(@NotBlank @Size(min = 16, max = 512) String token) {

        @Override
        public String toString() {
            return "TokenRequest[redacted]";
        }
    }

    record AcceptRequest(@NotBlank @Size(min = 16, max = 512) String token, @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "AcceptRequest[redacted]";
        }
    }

    record PreviewResponse(String clinicName, String maskedEmail, AccountKind kind, Instant expiresAt) {}

    @PublicEndpoint
    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody TokenRequest request) {
        InvitationService.Preview preview = invitations.preview(request.token());
        return new PreviewResponse(preview.clinicName(), preview.maskedEmail(), preview.kind(), preview.expiresAt());
    }

    @PublicEndpoint
    @PostMapping("/accept")
    Object accept(@Valid @RequestBody AcceptRequest request) {
        InvitationService.Accepted accepted = invitations.accept(request.token(), request.password());
        // Logged in like a login that matched only this clinic (contract LoginResult): tokens,
        // or the branch choice when the account has several branches (ADR-013).
        return LoginResponses.of(sessions.startTenantSession(accepted.tenantId(), accepted.accountId()));
    }
}
