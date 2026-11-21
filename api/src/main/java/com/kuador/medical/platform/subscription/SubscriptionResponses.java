package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response bodies of {@code /platform/tenants/{tenantId}/subscription/**} ({@code platform.openapi.yaml}). */
final class SubscriptionResponses {

    private SubscriptionResponses() {}

    /** {@code AccessMode} by status (ADR-017). */
    enum AccessMode {
        FULL,
        READ_ONLY,
        RETENTION,
        NONE;

        static AccessMode of(SubscriptionStatus status, boolean purged) {
            if (purged) {
                return NONE;
            }
            return switch (status) {
                case TRIAL, ACTIVE, PAST_DUE -> FULL;
                case SUSPENDED -> READ_ONLY;
                case CANCELLED -> RETENTION;
            };
        }
    }

    /** {@code PlanSummary}. */
    record PlanSummary(UUID id, String code, String name) {}

    /** {@code Subscription}. */
    record SubscriptionResponse(
            UUID id,
            SubscriptionStatus status,
            AccessMode accessMode,
            PlanSummary plan,
            BillingPeriod billingPeriod,
            @Nullable String priceAmount,
            String currency,
            @Nullable Instant currentPeriodStart,
            @Nullable Instant currentPeriodEnd,
            @Nullable Instant trialEndsAt,
            @Nullable Instant graceEndsAt,
            @Nullable Instant suspendedAt,
            @Nullable Instant cancelledAt,
            @Nullable Instant retentionEndsAt,
            @Nullable Instant reactivatedAt,
            @Nullable LocalDate priceLockedUntil,
            boolean purged,
            boolean reactivable,
            List<SubscriptionStatus> allowedTargets,
            @Nullable Object scheduledPlanChange,
            List<PeriodResponse> periods,
            List<TransitionResponse> transitions,
            long version) {}

    /** {@code SubscriptionPeriod}. */
    record PeriodResponse(String planName, Instant periodStart, Instant periodEnd, String amount, String currency) {}

    /** {@code SubscriptionTransition}. */
    record TransitionResponse(
            @Nullable SubscriptionStatus from,
            SubscriptionStatus to,
            TransitionCause cause,
            @Nullable String actor,
            @Nullable String reason,
            Instant at) {}

    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
