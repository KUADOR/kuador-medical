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
 * A data export of a clinic (FR-021, ADR-018). The file itself lives in the {@code content}
 * column, read and written only by {@link ExportFiles} so that listing or reading an export never
 * loads it. Tenant-scoped (ADR-007).
 */
@Entity
@Table(name = "tenant_export")
class TenantExport {

    /** {@code ExportStatus} in {@code platform.openapi.yaml}. */
    enum Status {
        PENDING,
        READY,
        FAILED,
        EXPIRED
    }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Column(name = "expires_at")
    private @Nullable Instant expiresAt;

    protected TenantExport() {}

    TenantExport(UUID id, ExportFormat format, UUID requestedBy, Instant createdAt) {
        this.id = id;
        this.format = format;
        this.status = Status.PENDING;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
    }

    /** The status seen at {@code now}: a ready export past its expiry is {@code EXPIRED}. */
    Status statusAt(Instant now) {
        return status == Status.READY && expiresAt != null && !now.isBefore(expiresAt) ? Status.EXPIRED : status;
    }

    UUID getId() {
        return id;
    }

    ExportFormat getFormat() {
        return format;
    }

    Status getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable Instant getExpiresAt() {
        return expiresAt;
    }
}
