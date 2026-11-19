package com.kuador.medical.identity.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Permission code of the catalog granted to a role. Tenant-scoped (ADR-007). */
@Entity
@Table(name = "identity_role_permission")
@IdClass(RolePermission.Key.class)
public class RolePermission {

    @Id
    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Id
    @Column(name = "permission_code", nullable = false, updatable = false)
    private String permissionCode;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    protected RolePermission() {}

    public RolePermission(UUID roleId, String permissionCode) {
        this.roleId = roleId;
        this.permissionCode = permissionCode;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {

        private UUID roleId;
        private String permissionCode;

        protected Key() {}

        public Key(UUID roleId, String permissionCode) {
            this.roleId = roleId;
            this.permissionCode = permissionCode;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(roleId, key.roleId) && Objects.equals(permissionCode, key.permissionCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permissionCode);
        }
    }
}
