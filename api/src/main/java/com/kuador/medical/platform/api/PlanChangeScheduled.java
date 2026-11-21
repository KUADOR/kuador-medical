package com.kuador.medical.platform.api;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.Instant;
import java.util.UUID;

/** Published when a downgrade or a billing period change is scheduled for the next renewal. */
public record PlanChangeScheduled(
        UUID tenantId,
        UUID subscriptionId,
        UUID planChangeId,
        PlanChangeKind kind,
        UUID fromPlanId,
        UUID toPlanId,
        BillingPeriod billingPeriod,
        Instant effectiveAt) {}
