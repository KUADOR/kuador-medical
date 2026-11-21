package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.BillingPeriod;
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

    /**
     * The newest notices whose price applies to a period starting at {@code periodStart}. Only
     * prices of the current plan and billing period count: after a plan change (MEDK-69) the
     * notices of the previous plan no longer apply.
     */
    @Query("""
            select n from PriceChangeNotice n
            where n.subscriptionId = :id and n.appliesFrom <= :periodStart
              and exists (select p.id from PlanPrice p
                          where p.id = n.planPriceId and p.planId = :planId and p.billingPeriod = :billingPeriod)
            order by n.notifiedAt desc, n.appliesFrom desc""")
    List<PriceChangeNotice> noticesApplyingAt(@Param("id") UUID subscriptionId, @Param("planId") UUID planId,
            @Param("billingPeriod") BillingPeriod billingPeriod, @Param("periodStart") Instant periodStart);

    /** The newest notice applying to a period starting at {@code periodStart}, if any. */
    default Optional<PriceChangeNotice> latestNoticeApplyingAt(UUID subscriptionId, UUID planId,
            BillingPeriod billingPeriod, Instant periodStart) {
        return noticesApplyingAt(subscriptionId, planId, billingPeriod, periodStart).stream().findFirst();
    }

    /** Notices of a price that applies from a renewal after {@code now}: the clinic is shown the upcoming price. */
    @Query("select n from PriceChangeNotice n where n.subscriptionId = :id and n.appliesFrom > :now order by n.appliesFrom")
    List<PriceChangeNotice> upcomingNotices(@Param("id") UUID subscriptionId, @Param("now") Instant now);
}
