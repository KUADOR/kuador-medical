package com.kuador.medical.audit;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Records security events (who, what, when, tenant). Only identifiers are stored, never
 * personal data. The event joins the transaction of the caller, so it is rolled back with it.
 */
public interface AuditRecorder {

    /**
     * @param action   what happened
     * @param tenantId clinic of the event, {@code null} for platform-level events
     * @param actorId  account that acted, {@code null} when unknown
     * @param target   what the action was applied to, {@code null} when nothing in particular
     */
    void record(AuditAction action, @Nullable UUID tenantId, @Nullable UUID actorId, @Nullable AuditTarget target);
}
