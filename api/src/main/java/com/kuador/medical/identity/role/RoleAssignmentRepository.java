package com.kuador.medical.identity.role;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Assignments of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    @Query("select a from RoleAssignment a where a.userId = :userId")
    List<RoleAssignment> findByUser(@Param("userId") UUID userId);

    @Query("select a from RoleAssignment a where a.roleId = :roleId")
    List<RoleAssignment> findByRole(@Param("roleId") UUID roleId);

    @Query("select count(a) > 0 from RoleAssignment a where a.roleId = :roleId")
    boolean existsByRole(@Param("roleId") UUID roleId);

    @Query("select count(a) > 0 from RoleAssignment a where a.roleId = :roleId and a.userId = :userId")
    boolean existsByRoleAndUser(@Param("roleId") UUID roleId, @Param("userId") UUID userId);

    /** Distinct users holding one of the roles, whose membership is active. */
    @Query("""
            select count(distinct a.userId) from RoleAssignment a, TenantMembership m
            where m.userAccountId = a.userId and m.status = com.kuador.medical.identity.membership.MembershipStatus.ACTIVE
            and a.roleId in :roleIds""")
    long countActiveUsersWithRoles(@Param("roleIds") Collection<UUID> roleIds);
}
