package com.kuador.medical.identity.membership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Role assigned to a membership. Tenant-scoped: both ends belong to the same clinic. */
@Entity
@Table(name = "membership_role")
@IdClass(MembershipRole.Key.class)
public class MembershipRole {

    @Id
    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Id
    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    protected MembershipRole() {}

    public MembershipRole(UUID membershipId, UUID roleId) {
        this.membershipId = membershipId;
        this.roleId = roleId;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {

        private UUID membershipId;
        private UUID roleId;

        protected Key() {}

        public Key(UUID membershipId, UUID roleId) {
            this.membershipId = membershipId;
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(membershipId, key.membershipId) && Objects.equals(roleId, key.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(membershipId, roleId);
        }
    }
}
