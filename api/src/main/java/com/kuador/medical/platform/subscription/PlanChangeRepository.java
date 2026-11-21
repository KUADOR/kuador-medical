package com.kuador.medical.platform.subscription;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Plan changes of the clinic bound in the tenant context (Hibernate filters by tenant). */
interface PlanChangeRepository extends Repository<PlanChange, UUID> {

    PlanChange save(PlanChange change);

    boolean existsById(UUID id);

    Optional<PlanChange> findById(UUID id);

    @Query("select c from PlanChange c where c.subscriptionId = :id and c.status = :status")
    Optional<PlanChange> findByStatus(@Param("id") UUID subscriptionId, @Param("status") PlanChange.Status status);

    /** The change waiting for the renewal, if any (at most one, unique index). */
    default Optional<PlanChange> findScheduled(UUID subscriptionId) {
        return findByStatus(subscriptionId, PlanChange.Status.SCHEDULED);
    }
}
