package com.kuador.medical.identity.role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Roles of the current clinic. Every method is a query, so @TenantId filters it (ADR-007);
 * {@code findById} is not used because a load by primary key is not a tenant-filtered query.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("select r from Role r where r.id = :id")
    Optional<Role> findInCurrentTenant(@Param("id") UUID id);

    @Query("select r from Role r")
    List<Role> findAllInCurrentTenant();

    @Query("select r from Role r where r.id in :ids")
    List<Role> findAllInCurrentTenant(@Param("ids") Collection<UUID> ids);

    @Query("select count(r) > 0 from Role r where lower(r.name) = lower(:name)")
    boolean existsByName(@Param("name") String name);

    @Query("select count(r) > 0 from Role r where lower(r.name) = lower(:name) and r.id <> :id")
    boolean existsByNameExcluding(@Param("name") String name, @Param("id") UUID id);
}
