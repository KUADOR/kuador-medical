package com.kuador.medical.patients.registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Guardian links of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
interface GuardianLinkRepository extends JpaRepository<GuardianLink, UUID> {

    @Query("select l from GuardianLink l where l.patientId = :patientId order by l.validFrom desc, l.id")
    List<GuardianLink> findByPatient(@Param("patientId") UUID patientId);

    @Query("select l from GuardianLink l where l.id = :id and l.patientId = :patientId")
    Optional<GuardianLink> findOfPatient(@Param("id") UUID id, @Param("patientId") UUID patientId);

    @Query("""
            select l from GuardianLink l
            where l.patientId = :patientId and l.linkType = :type and l.validTo is null""")
    List<GuardianLink> findCurrent(@Param("patientId") UUID patientId, @Param("type") LinkType type);
}
