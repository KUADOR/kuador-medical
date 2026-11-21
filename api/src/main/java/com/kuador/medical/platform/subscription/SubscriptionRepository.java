package com.kuador.medical.platform.subscription;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Subscriptions; Hibernate restricts every query to the clinic bound in the tenant context. */
interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** The subscription of the clinic bound in the tenant context. */
    @Query("select s from Subscription s")
    Optional<Subscription> findCurrent();
}
