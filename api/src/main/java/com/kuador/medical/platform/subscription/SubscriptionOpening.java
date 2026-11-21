package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.platform.plan.PlanCatalog.CurrentPrice;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.settings.PlatformSettingsService;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens the subscription of a clinic being onboarded (plan D4) and reads it back for the
 * platform. The clinic must be bound in the tenant context (ADR-007).
 */
@Service
public class SubscriptionOpening {

    private final EntityManager entityManager;
    private final SubscriptionRepository subscriptions;
    private final PlatformSettingsService settings;

    SubscriptionOpening(
            EntityManager entityManager, SubscriptionRepository subscriptions, PlatformSettingsService settings) {
        this.entityManager = entityManager;
        this.subscriptions = subscriptions;
        this.settings = settings;
    }

    /**
     * With the trial plan the subscription starts in TRIAL for the configured trial days; with a
     * paid plan it starts ACTIVE with a period from today at the price in force, kept as the
     * contracted price. A paid plan without a price for {@code billingPeriod} cannot be sold. The
     * initial status is recorded as a manual transition of the SUPER_ADMIN who onboards the clinic.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Subscription open(OfferedPlan plan, BillingPeriod billingPeriod, Instant now, UUID actorId, String actorName) {
        requireTenant();
        SubscriptionTransition.Actor actor = new SubscriptionTransition.Actor(actorId, actorName);
        if (plan.trial()) {
            Subscription trial = Subscription.trial(plan.id(), billingPeriod,
                    InitialTerms.trialEndsAt(now, settings.current().trialDays()), now);
            entityManager.persist(trial);
            entityManager.persist(new SubscriptionTransition(
                    trial.getId(), null, trial.getStatus(), TransitionCause.MANUAL, actor, null, null, now));
            return trial;
        }
        CurrentPrice price = plan.priceFor(billingPeriod).orElseThrow(() -> ApiException.validation(
                List.of(new FieldViolation("billingPeriod", "PRICE_NOT_AVAILABLE"))));
        Instant start = InitialTerms.periodStart(now);
        Instant end = InitialTerms.periodEnd(start, billingPeriod);
        Subscription active = Subscription.active(
                plan.id(), billingPeriod, price.amount(), price.currency(), start, end, now);
        entityManager.persist(active);
        entityManager.persist(new SubscriptionPeriod(
                active.getId(), plan.id(), start, end, price.amount(), price.currency()));
        entityManager.persist(new SubscriptionTransition(
                active.getId(), null, active.getStatus(), TransitionCause.MANUAL, actor, null, null, now));
        return active;
    }

    /** The subscription of the clinic bound in the tenant context. */
    @Transactional(readOnly = true)
    public Optional<Subscription> current() {
        requireTenant();
        return subscriptions.findCurrent();
    }

    private static UUID requireTenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }
}
