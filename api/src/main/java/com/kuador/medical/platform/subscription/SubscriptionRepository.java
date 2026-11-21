package com.kuador.medical.platform.subscription;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Subscriptions; Hibernate restricts every query to the clinic bound in the tenant context. */
interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** The subscription of the clinic bound in the tenant context. */
    @Query("select s from Subscription s")
    Optional<Subscription> findCurrent();

    /**
     * The subscription of the clinic, locked for update and with its version incremented: a
     * concurrent transition (a reactivation, plan D12) waits, then fails with a version conflict.
     */
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select s from Subscription s")
    Optional<Subscription> findCurrentForPurge();
}
