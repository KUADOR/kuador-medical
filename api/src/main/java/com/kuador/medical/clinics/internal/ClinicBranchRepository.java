package com.kuador.medical.clinics.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Branches of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
interface ClinicBranchRepository extends JpaRepository<ClinicBranch, UUID> {

    @Query("select b from ClinicBranch b order by lower(b.name), b.id")
    List<ClinicBranch> findAllInCurrentTenant();

    @Query("select b from ClinicBranch b where b.id in :ids order by lower(b.name), b.id")
    List<ClinicBranch> findInCurrentTenant(@Param("ids") Collection<UUID> ids);
}
