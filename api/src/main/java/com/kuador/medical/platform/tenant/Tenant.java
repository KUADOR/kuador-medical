package com.kuador.medical.platform.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A clinic. It is the tenant itself, so it carries no {@code @TenantId}. */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tenant() {}

    Tenant(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.status = TenantStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
