package com.kuador.medical.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The initial OWNER of a clinic onboarded by the SUPER_ADMIN (MEDK-67), for the platform module:
 * its invitation, whether its second factor is enabled, resending the invitation and resetting
 * the second factor (plan D7). Every method works on the clinic bound in the current request and
 * joins the caller's transaction. The invitation itself is created by identity when the platform
 * publishes {@link com.kuador.medical.shared.tenancy.TenantCreated} with an initial owner.
 */
public interface OwnerOnboarding {

    /** State of the OWNER onboarding of the current clinic; empty invitation for clinics without one. */
    OwnerStatus status();

    /**
     * Revokes the pending invitation and emails a new one, to {@code newEmail} when given (the
     * account email is corrected). Audited.
     *
     * @return id of the new invitation
     * @throws com.kuador.medical.shared.web.ApiException {@code NOT_FOUND} when the clinic has no
     *         OWNER invitation, {@code ACCOUNT_ALREADY_ACTIVE} once the OWNER accepted,
     *         {@code EMAIL_TAKEN} when the new email belongs to another account of the clinic
     */
    UUID resendInvitation(@Nullable String newEmail, UUID actor);

    /**
     * Ends the second factor of the OWNER, who enrolls again on its next request, and emails it.
     * The reason is kept with the reset; the audit trail records who and when.
     *
     * @throws com.kuador.medical.shared.web.ApiException {@code NOT_FOUND} when the clinic has no OWNER invitation
     */
    void resetTwoFactor(String reason, UUID actor);

    /** OWNER onboarding as the platform shows it. */
    record OwnerStatus(boolean twoFactorEnabled, @Nullable Invitation invitation) {}

    /** Latest OWNER invitation of the clinic. */
    record Invitation(UUID id, InvitationStatus status, String maskedEmail, Instant sentAt, Instant expiresAt) {

        public Invitation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
        }
    }

    enum InvitationStatus {
        PENDING,
        ACCEPTED,
        EXPIRED,
        REVOKED
    }
}
