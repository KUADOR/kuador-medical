package com.kuador.medical.platform.plan;

import org.springframework.modulith.NamedInterface;

/** Billing period of a price. The model accepts more periods later (for example quarterly). */
@NamedInterface("catalog")
public enum BillingPeriod {
    MONTHLY,
    ANNUAL
}
