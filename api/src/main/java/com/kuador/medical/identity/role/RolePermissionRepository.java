package com.kuador.medical.identity.role;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Permissions granted to the roles of the current clinic; filtered by @TenantId. */
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.Key> {

    List<RolePermission> findByRoleIdIn(Collection<UUID> roleIds);
}
