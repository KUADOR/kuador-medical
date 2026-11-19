package com.kuador.medical.clinics.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Branch (sucursal) of a clinic. Tenant-scoped (ADR-007). */
@Entity
@Table(name = "clinics_branch")
public class ClinicBranch {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClinicBranch() {}

    public ClinicBranch(String name) {
        this.id = UUID.randomUUID();
        this.name = name.strip();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
