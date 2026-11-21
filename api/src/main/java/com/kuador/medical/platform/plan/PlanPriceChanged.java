package com.kuador.medical.platform.plan;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a new price version of a plan is committed (ADR-015). The subscription
 * lifecycle uses it to notify the OWNER of affected clinics and schedule the new price at the
 * first renewal at least {@code priceChangeNoticeDays} after the notice (plan D1).
 */
public record PlanPriceChanged(UUID planId, UUID planPriceId, BillingPeriod billingPeriod, Instant effectiveFrom) {}
