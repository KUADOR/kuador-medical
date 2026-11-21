package com.kuador.medical.platform.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The purge of a clinic was confirmed and every {@link TenantDataPurger} ran (ADR-018). Published
 * inside the transaction of the confirmation. The clinic has no access any more ({@code NONE}).
 */
public record TenantPurged(UUID tenantId, UUID purgeRequestId, Instant at) {}
