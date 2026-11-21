package com.kuador.medical.platform.api;

import com.kuador.medical.platform.plan.LimitCode;

/**
 * SPI: the module that owns a resource counted by a plan limit reports its current usage (ADR-017,
 * plan D11: tenant-wide totals). One bean per limit; a limit without provider has no known usage
 * and is not enforced by {@link PlanCapabilities#assertCanCreate}.
 */
public interface PlanUsageProvider {

    /** The limit this provider counts. */
    LimitCode limit();

    /** Current usage of the clinic bound in the tenant context (ADR-007). */
    long currentUsage();
}
