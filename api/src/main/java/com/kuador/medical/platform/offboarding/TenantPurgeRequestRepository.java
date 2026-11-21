package com.kuador.medical.platform.offboarding;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Purge requests; Hibernate restricts every query to the clinic bound in the tenant context. */
interface TenantPurgeRequestRepository extends JpaRepository<TenantPurgeRequest, UUID> {

    @Query("select r from TenantPurgeRequest r where r.status = :status")
    List<TenantPurgeRequest> findByStatus(@Param("status") TenantPurgeRequest.Status status);
}
