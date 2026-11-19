package com.kuador.medical.identity.membership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Membership of an account in a clinic (ADR-010). Tenant-scoped (ADR-007). */
@Entity
@Table(name = "tenant_membership")
public class TenantMembership {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private UUID userAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TenantMembership() {}

    public TenantMembership(UUID userAccountId) {
        this.id = UUID.randomUUID();
        this.userAccountId = userAccountId;
        this.status = MembershipStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }
}
