package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.invitation.OwnerInvitationService;
import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /public/owner-invitations/preview} and {@code /accept} (MEDK-67, ADR-014): the
 * OWNER invitation token comes in the body, never in the path or the query. Accepting logs the
 * OWNER in with a session that still requires the TOTP second factor (ADR-019).
 */
@RestController
@RequestMapping("/public/owner-invitations")
class OwnerInvitationController {

    private final OwnerInvitationService invitations;
    private final SessionIssuer sessions;

    OwnerInvitationController(OwnerInvitationService invitations, SessionIssuer sessions) {
        this.invitations = invitations;
        this.sessions = sessions;
    }

    record TokenRequest(@NotBlank @Size(max = 512) String token) {

        @Override
        public String toString() {
            return "TokenRequest[redacted]";
        }
    }

    record AcceptRequest(
            @NotBlank @Size(max = 512) String token,
            @NotBlank @Size(max = 128) String password,
            @NotNull Boolean acceptTerms,
            @NotBlank @Size(max = 40) String termsVersion,
            @NotNull Boolean acceptDataPolicy,
            @NotBlank @Size(max = 40) String dataPolicyVersion) {

        @Override
        public String toString() {
            return "AcceptRequest[redacted]";
        }
    }

    record PreviewResponse(String clinicName, String ownerName, String maskedEmail, Instant expiresAt,
            String termsVersion, String dataPolicyVersion) {

        @Override
        public String toString() {
            return "PreviewResponse[redacted]";
        }
    }

    @PublicEndpoint
    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody TokenRequest request) {
        OwnerInvitationService.Preview preview = invitations.preview(request.token());
        return new PreviewResponse(preview.clinicName(), preview.ownerName(), preview.maskedEmail(),
                preview.expiresAt(), preview.termsVersion(), preview.dataPolicyVersion());
    }

    @PublicEndpoint
    @PostMapping("/accept")
    Object accept(@Valid @RequestBody AcceptRequest request) {
        OwnerInvitationService.Accepted accepted = invitations.accept(new OwnerInvitationService.Acceptance(
                request.token(), request.password(), request.acceptTerms(), request.termsVersion(),
                request.acceptDataPolicy(), request.dataPolicyVersion()));
        return LoginResponses.of(sessions.startTenantSession(accepted.tenantId(), accepted.accountId()));
    }
}
