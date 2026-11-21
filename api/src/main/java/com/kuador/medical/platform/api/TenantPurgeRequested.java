package com.kuador.medical.platform.api;

import java.time.Instant;
import java.util.UUID;

/** First step of the purge of a clinic (plan D6): requested by a SUPER_ADMIN; nothing is deleted yet. */
public record TenantPurgeRequested(UUID tenantId, UUID purgeRequestId, Instant at) {}
