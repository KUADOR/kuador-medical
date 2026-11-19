package com.kuador.medical.shared.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Records of the current clinic; every method is a query, so @TenantId filters it (ADR-007). */
interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Query("select r from IdempotencyRecord r where r.operation = :operation and r.idempotencyKey = :key")
    Optional<IdempotencyRecord> find(@Param("operation") String operation, @Param("key") String key);

    @Modifying(flushAutomatically = true)
    @Query("delete from IdempotencyRecord r where r.operation = :operation and r.idempotencyKey = :key")
    void delete(@Param("operation") String operation, @Param("key") String key);
}
