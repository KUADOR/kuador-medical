package com.kuador.medical.audit.internal;

import com.kuador.medical.audit.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Row of {@code audit_event}. The tenant is an explicit column, not {@code @TenantId}: platform
 * events have no tenant, and the recorder always receives it from the backend.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private @Nullable UUID tenantId;

    @Column(name = "actor_account_id", updatable = false)
    private @Nullable UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "target_type", updatable = false)
    private @Nullable String targetType;

    @Column(name = "target_id", updatable = false)
    private @Nullable UUID targetId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(
            AuditAction action,
            @Nullable UUID tenantId,
            @Nullable UUID actorAccountId,
            @Nullable String targetType,
            @Nullable UUID targetId,
            Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.action = action;
        this.tenantId = tenantId;
        this.actorAccountId = actorAccountId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.occurredAt = occurredAt;
    }
}
