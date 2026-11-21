package com.kuador.medical.platform.subscription;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Request bodies of {@code /platform/tenants/{tenantId}/subscription/**} ({@code platform.openapi.yaml}). */
final class SubscriptionRequests {

    private SubscriptionRequests() {}

    /** {@code TransitionRequest}: {@code reason} is mandatory to reactivate a CANCELLED subscription. */
    record TransitionRequest(
            @NotNull SubscriptionStatus target,
            @Nullable @Size(min = 1, max = 500) String reason,
            @NotNull @PositiveOrZero Long version) {}

    /** {@code PlanChangeRequest}: {@code version} is optional; when sent, a stale one responds 409. */
    record PlanChangeRequest(
            @Nullable UUID id,
            @NotNull UUID targetPlanId,
            @NotNull BillingPeriod billingPeriod,
            @Nullable @PositiveOrZero Long version) {}

    /** {@code PriceLockRequest}: {@code lockedUntil} is the last local day of the lock; null removes it. */
    record PriceLockRequest(
            @Nullable LocalDate lockedUntil,
            @Nullable @Size(min = 1, max = 500) String reason,
            @NotNull @PositiveOrZero Long version) {}
}
