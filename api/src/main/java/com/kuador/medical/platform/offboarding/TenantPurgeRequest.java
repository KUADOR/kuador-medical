package com.kuador.medical.platform.offboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Purge of a clinic in two steps (plan D6): {@code REQUESTED} by a SUPER_ADMIN, then
 * {@code CONFIRMED} by typing the RUC, or {@code CANCELLED} by a reactivation (plan D12). At most
 * one pending request per clinic. Tenant-scoped (ADR-007). The reason is never logged.
 */
@Entity
@Table(name = "tenant_purge_request")
class TenantPurgeRequest {

    /** {@code PurgeRequestStatus} in {@code platform.openapi.yaml}. */
    enum Status {
        REQUESTED,
        CONFIRMED,
        CANCELLED
    }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "confirmed_by")
    private @Nullable UUID confirmedBy;

    @Column(name = "confirmed_at")
    private @Nullable Instant confirmedAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    protected TenantPurgeRequest() {}

    TenantPurgeRequest(UUID id, String reason, UUID requestedBy, Instant requestedAt) {
        this.id = id;
        this.status = Status.REQUESTED;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    void confirm(UUID by, Instant at) {
        this.status = Status.CONFIRMED;
        this.confirmedBy = by;
        this.confirmedAt = at;
    }

    void cancel(Instant at) {
        this.status = Status.CANCELLED;
        this.cancelledAt = at;
    }

    UUID getId() {
        return id;
    }

    Status getStatus() {
        return status;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    @Nullable Instant getConfirmedAt() {
        return confirmedAt;
    }
}
