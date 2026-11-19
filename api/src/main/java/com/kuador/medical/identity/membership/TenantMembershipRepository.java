package com.kuador.medical.identity.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is filtered by the current tenant (@TenantId). */
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    Optional<TenantMembership> findByUserAccountIdAndStatus(UUID userAccountId, MembershipStatus status);

    @Query("""
            select r.name from Role r, MembershipRole mr
            where mr.roleId = r.id and mr.membershipId = :membershipId
            order by r.name desc""")
    List<String> roleNames(@Param("membershipId") UUID membershipId);

    @Query("""
            select distinct rp.permissionCode from RolePermission rp, MembershipRole mr
            where mr.roleId = rp.roleId and mr.membershipId = :membershipId""")
    List<String> permissionCodes(@Param("membershipId") UUID membershipId);
}
