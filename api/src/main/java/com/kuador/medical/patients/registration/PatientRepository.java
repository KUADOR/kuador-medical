package com.kuador.medical.patients.registration;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Patients of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
interface PatientRepository extends JpaRepository<Patient, UUID> {

    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> findInCurrentTenant(@Param("id") UUID id);

    /** The patient locked until the end of the transaction, to serialize changes of its guardians. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> lockInCurrentTenant(@Param("id") UUID id);

    @Query("select count(p) > 0 from Patient p where p.documentType = :type and p.documentNumber = :number")
    boolean existsByDocument(@Param("type") String documentType, @Param("number") String documentNumber);

    @Query("""
            select count(p) > 0 from Patient p
            where p.documentType = :type and p.documentNumber = :number and p.id <> :id""")
    boolean existsByDocumentExcept(
            @Param("type") String documentType, @Param("number") String documentNumber, @Param("id") UUID id);
}
