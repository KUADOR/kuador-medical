package com.kuador.medical.patients.registration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consents of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /** Consents not revoked of the patient, newest first. */
    @Query("""
            select c from Consent c where c.patientId = :patientId and c.revokedAt is null
            order by c.grantedAt desc, c.id""")
    List<Consent> findCurrent(@Param("patientId") UUID patientId, Limit limit);

    @Query("select c from Consent c where c.id = :id")
    java.util.Optional<Consent> findInCurrentTenant(@Param("id") UUID id);
}
