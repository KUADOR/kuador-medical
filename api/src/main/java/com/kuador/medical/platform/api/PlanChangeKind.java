package com.kuador.medical.platform.api;

/**
 * Kind of a plan change (plan D2, {@code PlanChangeKind} in {@code platform.openapi.yaml}): a higher
 * price in the same billing period is an upgrade, applied at once with proration; a lower or equal
 * price is a downgrade and another billing period a period change, both applied at the renewal.
 */
public enum PlanChangeKind {
    UPGRADE,
    DOWNGRADE,
    PERIOD_CHANGE
}
