package com.kuador.medical.platform.api;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a plan change other than an upgrade takes effect: a scheduled downgrade or period
 * change at the renewal, or any change during the trial or while past due (applied at once).
 */
public record PlanChangeApplied(
        UUID tenantId,
        UUID subscriptionId,
        UUID planChangeId,
        PlanChangeKind kind,
        UUID fromPlanId,
        UUID toPlanId,
        BillingPeriod billingPeriod,
        Instant at) {}
