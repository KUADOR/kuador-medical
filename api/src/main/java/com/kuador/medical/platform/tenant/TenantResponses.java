package com.kuador.medical.platform.tenant;

import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.platform.subscription.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Responses of {@code /platform/tenants} (contracts/platform.openapi.yaml). */
final class TenantResponses {

    private TenantResponses() {}

    /** {@code Tenant}. */
    record TenantResponse(
            UUID id,
            String legalName,
            @Nullable String tradeName,
            String ruc,
            ClinicType clinicType,
            String countryCode,
            String city,
            String address,
            String phone,
            String contactEmail,
            @Nullable String mspEstablishmentNumber,
            OwnerResponse owner,
            SubscriptionSummary subscription,
            Instant createdAt,
            long version) {

        @Override
        public String toString() {
            return "TenantResponse[redacted]";
        }
    }

    /** {@code TenantOwner}: invitation and second factor of the initial OWNER (MEDK-67). */
    record OwnerResponse(
            String fullName, String maskedEmail, boolean twoFactorEnabled, @Nullable InvitationResponse invitation) {

        @Override
        public String toString() {
            return "OwnerResponse[redacted]";
        }
    }

    /** {@code OwnerInvitation}. */
    record InvitationResponse(
            com.kuador.medical.identity.OwnerOnboarding.InvitationStatus status,
            String maskedEmail,
            Instant sentAt,
            Instant expiresAt) {

        static @Nullable InvitationResponse of(com.kuador.medical.identity.OwnerOnboarding.@Nullable Invitation invitation) {
            return invitation == null ? null : new InvitationResponse(
                    invitation.status(), invitation.maskedEmail(), invitation.sentAt(), invitation.expiresAt());
        }
    }

    /** {@code TenantSubscriptionSummary}. */
    record SubscriptionSummary(
            SubscriptionStatus status,
            UUID planId,
            String planName,
            BillingPeriod billingPeriod,
            @Nullable Instant currentPeriodEnd,
            @Nullable Instant trialEndsAt) {}

    /** {@code TenantListItem}. */
    record TenantListItem(
            UUID id,
            String legalName,
            @Nullable String tradeName,
            String ruc,
            ClinicType clinicType,
            String planName,
            SubscriptionStatus subscriptionStatus,
            Instant createdAt) {

        @Override
        public String toString() {
            return "TenantListItem[redacted]";
        }
    }

    /** {@code TenantPage}. */
    record TenantPage(int page, int size, long totalItems, List<TenantListItem> items) {}

    /** {@code j***@example.com}: first character of the local part and the domain. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
