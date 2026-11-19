package com.kuador.medical.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A creation already done under an {@code Idempotency-Key}. Tenant-scoped (ADR-007). */
@Entity
@Table(name = "idempotency_record")
class IdempotencyRecord {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    IdempotencyRecord(String operation, String idempotencyKey, String requestHash, UUID resourceId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
    }

    String getRequestHash() {
        return requestHash;
    }

    UUID getResourceId() {
        return resourceId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
