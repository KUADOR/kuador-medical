package com.kuador.medical.patients.registration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Guardians of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
interface GuardianRepository extends JpaRepository<Guardian, UUID> {

    @Query("select g from Guardian g where g.id = :id")
    Optional<Guardian> findInCurrentTenant(@Param("id") UUID id);

    @Query("select g from Guardian g where g.id in :ids")
    List<Guardian> findAllInCurrentTenant(@Param("ids") Collection<UUID> ids);

    @Query("select g from Guardian g where g.documentType = :type and g.documentNumber = :number")
    Optional<Guardian> findByDocument(@Param("type") String documentType, @Param("number") String documentNumber);
}
