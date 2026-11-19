package com.kuador.medical.identity.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** Role of a clinic. Tenant-scoped (ADR-007). */
@Entity
@Table(name = "role")
public class Role {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private @Nullable String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_code", updatable = false)
    private @Nullable SystemRole systemCode;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Role() {}

    private Role(String name, @Nullable String description, @Nullable SystemRole systemCode) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.systemCode = systemCode;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Role system(SystemRole systemRole) {
        return new Role(systemRole.name(), systemRole.description(), systemRole);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable SystemRole getSystemCode() {
        return systemCode;
    }

    public long getVersion() {
        return version;
    }
}
