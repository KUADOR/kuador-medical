package com.kuador.medical.platform.plan;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Immutable price versions of the plans. */
interface PlanPriceRepository extends JpaRepository<PlanPrice, UUID> {

    @Query("select p from PlanPrice p where p.planId = :planId order by p.effectiveFrom desc, p.createdAt desc")
    List<PlanPrice> findHistory(@Param("planId") UUID planId);

    @Query("select p from PlanPrice p where p.planId in :planIds")
    List<PlanPrice> findByPlanIds(@Param("planIds") Collection<UUID> planIds);
}
