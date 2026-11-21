package com.kuador.medical.platform.api;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the transaction of an upgrade, applied at once (plan D3). {@code proratedAmount}
 * is (new − contracted price) × remaining days ÷ period days, without VAT: the billing epic charges it.
 */
public record PlanUpgraded(
        UUID tenantId,
        UUID subscriptionId,
        UUID planChangeId,
        UUID fromPlanId,
        UUID toPlanId,
        BillingPeriod billingPeriod,
        BigDecimal proratedAmount,
        String currency,
        Instant at) {}
