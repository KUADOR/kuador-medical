package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.account.InvitationService;
import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
    private final AccessTokenService tokens;

    InvitationController(InvitationService invitations, AccessTokenService tokens) {
        this.invitations = invitations;
        this.tokens = tokens;
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

    record ClinicSummary(UUID id, String name) {}

    /**
     * {@code AuthenticatedResult} of the contract. The refresh token and the active branch arrive
     * with MEDK-15; until then the session is the access token of ADR-006.
     */
    record AcceptedResponse(
            String status,
            String accessToken,
            String tokenType,
            long expiresIn,
            AccountScope scope,
            ClinicSummary clinic,
            @Nullable Object branch,
            boolean mustChangePassword) {

        @Override
        public String toString() {
            return "AcceptedResponse[redacted]";
        }
    }

    @PublicEndpoint
    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody TokenRequest request) {
        InvitationService.Preview preview = invitations.preview(request.token());
        return new PreviewResponse(preview.clinicName(), preview.maskedEmail(), preview.kind(), preview.expiresAt());
    }

    @PublicEndpoint
    @PostMapping("/accept")
    AcceptedResponse accept(@Valid @RequestBody AcceptRequest request) {
        InvitationService.Accepted accepted = invitations.accept(request.token(), request.password());
        IssuedToken token = tokens.issue(accepted.accountId(), AccountScope.TENANT, accepted.tenantId());
        return new AcceptedResponse(
                "AUTHENTICATED", token.value(), "Bearer", token.expiresInSeconds(), AccountScope.TENANT,
                new ClinicSummary(accepted.tenantId(), accepted.clinicName()), null, false);
    }
}
