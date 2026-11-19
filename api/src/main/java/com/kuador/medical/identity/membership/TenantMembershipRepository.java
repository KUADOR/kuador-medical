package com.kuador.medical.identity.membership;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is filtered by the current tenant (@TenantId). */
public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    Optional<TenantMembership> findByUserAccountIdAndStatus(UUID userAccountId, MembershipStatus status);

    @Query("select m from TenantMembership m where m.userAccountId = :userAccountId")
    Optional<TenantMembership> findByUserAccountId(@Param("userAccountId") UUID userAccountId);
}
