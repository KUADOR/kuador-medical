package com.kuador.medical.platform.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Periods, transitions and price notices of the clinic bound in the tenant context (Hibernate filters by tenant). */
interface SubscriptionHistoryRepository extends Repository<SubscriptionTransition, UUID> {

    @Query("select t from SubscriptionTransition t where t.subscriptionId = :id order by t.at desc, t.createdAt desc")
    List<SubscriptionTransition> transitions(@Param("id") UUID subscriptionId);

    @Query("select count(t) > 0 from SubscriptionTransition t where t.paymentReference = :reference")
    boolean paymentApplied(@Param("reference") String paymentReference);

    @Query("select p from SubscriptionPeriod p where p.subscriptionId = :id order by p.periodStart desc, p.createdAt desc")
    List<SubscriptionPeriod> periods(@Param("id") UUID subscriptionId);

    @Query("select count(n) > 0 from PriceChangeNotice n where n.subscriptionId = :id and n.planPriceId = :priceId")
    boolean noticed(@Param("id") UUID subscriptionId, @Param("priceId") UUID planPriceId);

    /** The newest notice whose price applies to a period starting at {@code periodStart}. */
    @Query("""
            select n from PriceChangeNotice n
            where n.subscriptionId = :id and n.appliesFrom <= :periodStart
            order by n.notifiedAt desc, n.appliesFrom desc""")
    List<PriceChangeNotice> noticesApplyingAt(@Param("id") UUID subscriptionId, @Param("periodStart") Instant periodStart);

    /** The newest notice applying to a period starting at {@code periodStart}, if any. */
    default Optional<PriceChangeNotice> latestNoticeApplyingAt(UUID subscriptionId, Instant periodStart) {
        return noticesApplyingAt(subscriptionId, periodStart).stream().findFirst();
    }
}
