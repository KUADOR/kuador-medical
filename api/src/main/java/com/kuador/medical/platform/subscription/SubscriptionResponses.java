package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.api.PlanChangeKind;
import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.platform.plan.ModuleCode;
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
            @Nullable PlanChangeResponse scheduledPlanChange,
            List<PeriodResponse> periods,
            List<TransitionResponse> transitions,
            long version) {}

    /** {@code PlanChange}. */
    record PlanChangeResponse(
            UUID id,
            PlanChangeKind kind,
            PlanChange.Status status,
            PlanSummary fromPlan,
            PlanSummary toPlan,
            BillingPeriod billingPeriod,
            Instant requestedAt,
            Instant effectiveAt,
            @Nullable String proratedAmount,
            String currency) {}

    /** {@code LimitExcess}. */
    record LimitExcess(LimitCode limit, long limitValue, long usage) {}

    /** {@code PlanChangePreview}. */
    record PlanChangePreviewResponse(
            PlanChangeKind kind,
            PlanSummary targetPlan,
            BillingPeriod billingPeriod,
            Instant effectiveAt,
            @Nullable String proratedAmount,
            String currency,
            String newPriceAmount,
            List<LimitExcess> excess,
            List<ModuleCode> disabledModules) {}

    /** {@code LimitUsage}: {@code limitValue} null is unlimited, {@code usage} null is unknown. */
    record LimitUsage(LimitCode limit, @Nullable Long limitValue, @Nullable Long usage) {}

    /** {@code Notice}. */
    record Notice(NoticeKind kind, @Nullable Instant effectiveAt) {}

    /** {@code NoticeKind}. */
    enum NoticeKind {
        TRIAL_ENDING,
        PAYMENT_OVERDUE,
        SUSPENDED,
        CANCELLED,
        PRICE_CHANGE,
        DOWNGRADE_EXCESS
    }

    /** {@code ClinicSubscription}: the clinic's own view ({@code GET /tenant/subscription}). */
    record ClinicSubscriptionResponse(
            SubscriptionStatus status,
            AccessMode accessMode,
            PlanSummary plan,
            BillingPeriod billingPeriod,
            @Nullable String priceAmount,
            String currency,
            @Nullable Instant currentPeriodEnd,
            @Nullable Instant trialEndsAt,
            @Nullable Instant graceEndsAt,
            @Nullable Instant retentionEndsAt,
            List<ModuleCode> modules,
            List<LimitUsage> limits,
            @Nullable PlanChangeResponse scheduledPlanChange,
            List<Notice> notices) {}

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
