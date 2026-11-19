package com.kuador.medical.identity.account;

import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.ClinicAccountStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accounts of the current clinic. Every method is a query, so @TenantId filters it (ADR-007). */
public interface ClinicAccountRepository extends JpaRepository<ClinicAccount, UUID> {

    @Query("select a from ClinicAccount a where a.id = :id")
    Optional<ClinicAccount> findInCurrentTenant(@Param("id") UUID id);

    @Query("select a from ClinicAccount a where a.id = :id and a.kind = :kind")
    Optional<ClinicAccount> findInCurrentTenant(@Param("id") UUID id, @Param("kind") AccountKind kind);

    /** {@code email} must already be normalized ({@link EmailAddresses#normalize}). */
    @Query("select count(a) > 0 from ClinicAccount a where a.email = :email")
    boolean existsByEmailInCurrentTenant(@Param("email") String email);

    @Query("select count(a) from ClinicAccount a where a.kind = :kind and a.status in :statuses")
    long countByKindAndStatus(@Param("kind") AccountKind kind, @Param("statuses") Collection<ClinicAccountStatus> statuses);
}
