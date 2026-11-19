package com.kuador.medical.identity.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * User + branch + role (ADR-013). Tenant-scoped (ADR-007). {@code branchId} is null only for the
 * clinic-wide OWNER assignment. The branch belongs to the clinics module and is stored as an id.
 */
@Entity
@Table(name = "identity_role_assignment")
public class RoleAssignment {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "branch_id", updatable = false)
    private @Nullable UUID branchId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoleAssignment() {}

    public RoleAssignment(UUID userId, @Nullable UUID branchId, UUID roleId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.branchId = branchId;
        this.roleId = roleId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public @Nullable UUID getBranchId() {
        return branchId;
    }

    public UUID getRoleId() {
        return roleId;
    }
}
