package com.kuador.medical.identity.membership;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Role assignments of the current clinic; filtered by @TenantId. */
public interface MembershipRoleRepository extends JpaRepository<MembershipRole, MembershipRole.Key> {

    boolean existsByRoleId(UUID roleId);
}
