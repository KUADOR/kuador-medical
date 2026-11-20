package com.kuador.medical.platform.tenant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("select t.id from Tenant t where t.status = :status order by t.id")
    List<UUID> findIdsByStatus(@Param("status") TenantStatus status);
}
