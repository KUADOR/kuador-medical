package com.kuador.medical.platform.api;

import java.time.Instant;
import java.util.UUID;

/** A pending purge request was cancelled because the clinic was reactivated (plan D12). */
public record TenantPurgeCancelled(UUID tenantId, UUID purgeRequestId, Instant at) {}
