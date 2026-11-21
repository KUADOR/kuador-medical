package com.kuador.medical.platform.plan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Plans of the platform (no tenant). */
interface PlanRepository extends JpaRepository<Plan, UUID> {

    @Query("select p from Plan p order by p.trial desc, p.createdAt, p.code")
    List<Plan> findAllOrdered();

    @Query("select p from Plan p where p.status = :status order by p.trial desc, p.createdAt, p.code")
    List<Plan> findByStatusOrdered(@Param("status") PlanStatus status);

    boolean existsByCode(String code);

    @Query("select count(p) > 0 from Plan p where p.trial = true")
    boolean existsTrialPlan();
}
