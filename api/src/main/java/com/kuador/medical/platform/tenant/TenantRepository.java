package com.kuador.medical.platform.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("select t.id from Tenant t where t.status = :status order by t.id")
    List<UUID> findIdsByStatus(@Param("status") TenantStatus status);

    /** A clinic onboarded by the platform and not purged. */
    @Query("select t from Tenant t where t.id = :id and t.ruc is not null and t.purgedAt is null")
    Optional<Tenant> findOnboarded(@Param("id") UUID id);

    /** A clinic onboarded by the platform, purged or not. */
    @Query("select count(t) > 0 from Tenant t where t.id = :id and t.ruc is not null")
    boolean existsOnboarded(@Param("id") UUID id);

    @Query("""
            select count(t) > 0 from Tenant t
            where t.ruc = :ruc and t.purgedAt is null and (:except is null or t.id <> :except)""")
    boolean existsNotPurgedByRuc(@Param("ruc") String ruc, @Param("except") @Nullable UUID except);
}
