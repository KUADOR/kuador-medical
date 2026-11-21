package com.kuador.medical.platform.subscription;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.CurrentPrice;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.plan.PlanCatalog.PriceVersion;
import com.kuador.medical.platform.settings.PlatformSettings;
import com.kuador.medical.platform.settings.PlatformSettingsService;
import com.kuador.medical.platform.subscription.SubscriptionTransition.Actor;
import com.kuador.medical.platform.tenant.TenantRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place where a subscription changes status (ADR-016): manual transitions of the
 * SUPER_ADMIN, payment events of the billing epic and the dates reached by the lifecycle job. Every
 * change follows {@link SubscriptionStateMachine} (and {@link ReactivationPolicy} out of
 * CANCELLED), is recorded in {@code subscription_transition}, audited, published as
 * {@link SubscriptionStatusChanged} and notified to the OWNER after commit. Becoming ACTIVE opens a
 * new contracted period (ADR-015). Each method runs in the caller's transaction with the clinic
 * bound in the tenant context (ADR-007); the subscription is versioned (optimistic locking).
 */
@Service
public class SubscriptionLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycle.class);

    /** Enough for a job that was down long enough to cross every deadline (ACTIVE → PAST_DUE → SUSPENDED → CANCELLED). */
    private static final int MAX_STEPS = 4;

    private final EntityManager entityManager;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionHistoryRepository history;
    private final PlanCatalog plans;
    private final PlatformSettingsService settings;
    private final TenantRepository tenants;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final SubscriptionNotifications notifications;

    SubscriptionLifecycle(
            EntityManager entityManager,
            SubscriptionRepository subscriptions,
            SubscriptionHistoryRepository history,
            PlanCatalog plans,
            PlatformSettingsService settings,
            TenantRepository tenants,
            AuditRecorder audit,
            ApplicationEventPublisher events,
            SubscriptionNotifications notifications) {
        this.entityManager = entityManager;
        this.subscriptions = subscriptions;
        this.history = history;
        this.plans = plans;
        this.settings = settings;
        this.tenants = tenants;
        this.audit = audit;
        this.events = events;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ manual

    /**
     * {@code POST .../subscription/transitions}: moves the subscription to {@code target} as the
     * SUPER_ADMIN {@code actor}, if {@code version} is the current one.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Subscription transition(SubscriptionStatus target, @Nullable String reason, long version, Actor actor, Instant now) {
        Subscription subscription = current();
        if (subscription.getVersion() != version) {
            throw versionConflict();
        }
        SubscriptionStatus from = subscription.getStatus();
        SubscriptionStateMachine.check(from, target, TransitionCause.MANUAL);
        if (from == SubscriptionStatus.CANCELLED) {
            ReactivationPolicy.check(subscription.getRetentionEndsAt(), purged(), reason, now);
        }
        PlatformSettings periods = settings.current();
        switch (target) {
            case ACTIVE -> activate(subscription, now, from == SubscriptionStatus.PAST_DUE);
            case SUSPENDED -> subscription.suspend(now, now);
            case CANCELLED -> subscription.cancel(now, LifecycleDates.daysAfter(now, periods.retentionDays()), now);
            default -> throw SubscriptionStateMachine.invalid(from, target);
        }
        record(subscription, from, TransitionCause.MANUAL, actor, reason, null, now, periods);
        return subscription;
    }

    /** {@code PUT .../subscription/price-lock}: {@code null} removes the lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    Subscription lockPrice(@Nullable LocalDate lockedUntil, long version, UUID actorId, Instant now) {
        Subscription subscription = current();
        if (subscription.getVersion() != version) {
            throw versionConflict();
        }
        subscription.lockPrice(lockedUntil == null ? null : LifecycleDates.lockInstant(lockedUntil), now);
        flush();
        audit.record(AuditAction.SUBSCRIPTION_PRICE_LOCK_UPDATED, tenantId(), actorId,
                AuditTarget.subscription(subscription.getId()));
        return subscription;
    }

    // ------------------------------------------------------------------ payment events

    /**
     * {@link PaymentReceived}: TRIAL, PAST_DUE and SUSPENDED become ACTIVE and ACTIVE renews; a
     * CANCELLED clinic is never reactivated by a payment (plan D12): the payment is audited as
     * rejected for the SUPER_ADMIN to handle. A reference already applied changes nothing.
     *
     * @return whether the subscription changed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean paymentReceived(PaymentReceived payment, Instant now) {
        Optional<Subscription> found = subscriptions.findCurrent();
        if (found.isEmpty() || history.paymentApplied(payment.paymentReference())) {
            return false;
        }
        Subscription subscription = found.get();
        SubscriptionStatus from = subscription.getStatus();
        if (!SubscriptionStateMachine.allows(from, SubscriptionStatus.ACTIVE, TransitionCause.PAYMENT_RECEIVED)) {
            audit.record(AuditAction.SUBSCRIPTION_PAYMENT_REJECTED, tenantId(), null,
                    AuditTarget.subscription(subscription.getId()));
            log.warn("PaymentReceived rejected for tenant {}: subscription is {}", tenantId(), from);
            return false;
        }
        boolean renewal = from == SubscriptionStatus.ACTIVE;
        try {
            activate(subscription, now, renewal || from == SubscriptionStatus.PAST_DUE);
        } catch (ApiException e) {
            audit.record(AuditAction.SUBSCRIPTION_PAYMENT_REJECTED, tenantId(), null,
                    AuditTarget.subscription(subscription.getId()));
            log.warn("PaymentReceived rejected for tenant {}: {}", tenantId(), e.getMessage());
            return false;
        }
        record(subscription, from, TransitionCause.PAYMENT_RECEIVED, null, null, payment.paymentReference(), now,
                settings.current());
        return true;
    }

    /** {@link PaymentFailed}: audited; the status does not change (plan D5). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentFailed(PaymentFailed payment) {
        subscriptions.findCurrent().ifPresent(subscription -> audit.record(AuditAction.SUBSCRIPTION_PAYMENT_FAILED,
                tenantId(), null, AuditTarget.subscription(subscription.getId())));
    }

    // ------------------------------------------------------------------ scheduler

    /**
     * Applies every deadline reached at {@code now}: end of trial (→ SUSPENDED), end of period
     * (→ PAST_DUE), end of grace (→ SUSPENDED) and end of suspension (→ CANCELLED). Each step takes
     * the deadline as its instant, so running it twice, or late, gives the same result.
     *
     * @return the number of transitions applied
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int advance(Instant now) {
        Optional<Subscription> found = subscriptions.findCurrent();
        if (found.isEmpty()) {
            return 0;
        }
        Subscription subscription = found.get();
        PlatformSettings periods = settings.current();
        int applied = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            SubscriptionStatus from = subscription.getStatus();
            Instant due = dueAt(subscription, periods);
            if (due == null || due.isAfter(now)) {
                break;
            }
            switch (from) {
                case TRIAL -> subscription.suspend(due, now);
                case ACTIVE -> subscription.markPastDue(LifecycleDates.daysAfter(due, periods.graceDays()), now);
                case PAST_DUE -> subscription.suspend(due, now);
                case SUSPENDED -> subscription.cancel(due, LifecycleDates.daysAfter(due, periods.retentionDays()), now);
                case CANCELLED -> throw new IllegalStateException("A cancelled subscription has no deadline");
            }
            SubscriptionStateMachine.check(from, subscription.getStatus(), TransitionCause.SCHEDULER);
            record(subscription, from, TransitionCause.SCHEDULER, null, null, null, due, periods);
            applied++;
        }
        return applied;
    }

    /** The next deadline of the status, or {@code null} when none applies. */
    static @Nullable Instant dueAt(Subscription subscription, PlatformSettings periods) {
        return switch (subscription.getStatus()) {
            case TRIAL -> subscription.getTrialEndsAt();
            case ACTIVE -> subscription.getCurrentPeriodEnd();
            case PAST_DUE -> subscription.getGraceEndsAt();
            case SUSPENDED -> subscription.getSuspendedAt() == null ? null
                    : LifecycleDates.daysAfter(subscription.getSuspendedAt(), periods.suspensionDaysBeforeCancel());
            case CANCELLED -> null;
        };
    }

    /**
     * Notifies the OWNER of the newest price version of its plan when it differs from the
     * contracted price and was not notified yet, and schedules it for the first renewal at least
     * {@code priceChangeNoticeDays} later (plan D1). Only ACTIVE and PAST_DUE subscriptions have a
     * renewal to schedule; the others take the price in force when they become ACTIVE again.
     *
     * @return whether a notice was sent
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean noticePriceChange(Instant now) {
        Optional<Subscription> found = subscriptions.findCurrent();
        if (found.isEmpty()) {
            return false;
        }
        Subscription subscription = found.get();
        BigDecimal contracted = subscription.getPriceAmountSnapshot();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        if ((subscription.getStatus() != SubscriptionStatus.ACTIVE && subscription.getStatus() != SubscriptionStatus.PAST_DUE)
                || contracted == null || periodEnd == null) {
            return false;
        }
        Optional<PriceVersion> latest = plans.latestPrice(subscription.getPlanId(), subscription.getBillingPeriod());
        if (latest.isEmpty() || latest.get().amount().compareTo(contracted) == 0
                || history.noticed(subscription.getId(), latest.get().priceId())) {
            return false;
        }
        PriceVersion price = latest.get();
        Instant appliesFrom = LifecycleDates.priceChangeAppliesFrom(periodEnd, subscription.getBillingPeriod(), now,
                settings.current().priceChangeNoticeDays(), price.effectiveFrom(), lockedUntil(subscription));
        entityManager.persist(new PriceChangeNotice(
                subscription.getId(), price.priceId(), price.amount(), price.currency(), now, appliesFrom));
        flush();
        audit.record(AuditAction.PRICE_CHANGE_NOTICE_SENT, tenantId(), null, AuditTarget.subscription(subscription.getId()));
        notifications.priceChange(tenantId(), planName(subscription), subscription.getBillingPeriod(), contracted,
                price.amount(), price.currency(), appliesFrom);
        return true;
    }

    // ------------------------------------------------------------------ reads

    /** The subscription of the clinic bound in the tenant context. */
    @Transactional(readOnly = true)
    public Subscription current() {
        requireTenant();
        return subscriptions.findCurrent().orElseThrow(ApiException::notFound);
    }

    /** Whether the clinic bound in the tenant context was purged. */
    boolean purged() {
        return tenants.findById(tenantId()).map(tenant -> tenant.getPurgedAt() != null).orElse(false);
    }

    static @Nullable LocalDate lockedUntil(Subscription subscription) {
        Instant stored = subscription.getPriceLockedUntil();
        return stored == null ? null : LifecycleDates.lockedUntil(stored);
    }

    // ------------------------------------------------------------------ internals

    /**
     * ACTIVE with a new period. A renewal (ACTIVE, or PAST_DUE whose access never stopped) continues
     * from the end of the current period; otherwise (TRIAL, SUSPENDED, CANCELLED) the period starts
     * today.
     */
    private void activate(Subscription subscription, Instant now, boolean continuesPeriod) {
        Instant start = continuesPeriod && subscription.getCurrentPeriodEnd() != null
                ? subscription.getCurrentPeriodEnd()
                : LifecycleDates.startOfDay(now);
        Instant end = InitialTerms.periodEnd(start, subscription.getBillingPeriod());
        ContractedPrice price = priceFor(subscription, start, continuesPeriod);
        subscription.activate(start, end, price.amount(), price.currency(), now);
        entityManager.persist(new SubscriptionPeriod(
                subscription.getId(), subscription.getPlanId(), start, end, price.amount(), price.currency()));
    }

    /**
     * Price of a new period (ADR-015, plan D1 and D12). A price lock covering the period keeps the
     * contracted price. A renewal keeps the contracted price unless the OWNER was notified of a new
     * one applying from that period. A fresh period (from TRIAL, SUSPENDED or CANCELLED) takes the
     * price in force of the plan.
     */
    private ContractedPrice priceFor(Subscription subscription, Instant periodStart, boolean renewal) {
        BigDecimal contracted = subscription.getPriceAmountSnapshot();
        String currency = subscription.getPriceCurrencySnapshot();
        if (contracted != null && currency != null) {
            if (LifecycleDates.lockCovers(lockedUntil(subscription), periodStart)) {
                return new ContractedPrice(contracted, currency);
            }
            if (renewal) {
                return history.latestNoticeApplyingAt(subscription.getId(), periodStart)
                        .map(notice -> new ContractedPrice(notice.getAmount(), notice.getCurrency()))
                        .orElse(new ContractedPrice(contracted, currency));
            }
        }
        OfferedPlan plan = plans.find(subscription.getPlanId()).orElseThrow(ApiException::notFound);
        CurrentPrice price = plan.priceFor(subscription.getBillingPeriod()).orElseThrow(() -> ApiException.conflict(
                ErrorCode.INVALID_SUBSCRIPTION_TRANSITION,
                "The plan has no price for the billing period; change the plan before activating it."));
        return new ContractedPrice(price.amount(), price.currency());
    }

    private void record(Subscription subscription, SubscriptionStatus from, TransitionCause cause,
            @Nullable Actor actor, @Nullable String reason, @Nullable String paymentReference, Instant at,
            PlatformSettings periods) {
        entityManager.persist(new SubscriptionTransition(
                subscription.getId(), from, subscription.getStatus(), cause, actor, reason, paymentReference, at));
        flush();
        UUID tenantId = tenantId();
        UUID actorId = actor == null ? null : actor.id();
        boolean renewed = from == SubscriptionStatus.ACTIVE && subscription.getStatus() == SubscriptionStatus.ACTIVE;
        audit.record(renewed ? AuditAction.SUBSCRIPTION_RENEWED : AuditAction.SUBSCRIPTION_STATUS_CHANGED, tenantId,
                actorId, AuditTarget.subscription(subscription.getId()));
        if (!renewed) {
            events.publishEvent(new SubscriptionStatusChanged(
                    tenantId, subscription.getId(), from, subscription.getStatus(), cause, at));
            notifications.statusChanged(tenantId, subscription, from, planName(subscription),
                    periods.suspensionDaysBeforeCancel());
        }
    }

    private String planName(Subscription subscription) {
        return plans.find(subscription.getPlanId()).map(OfferedPlan::name).orElse("");
    }

    private void flush() {
        try {
            entityManager.flush();
        } catch (OptimisticLockException e) {
            throw versionConflict();
        }
    }

    private static UUID tenantId() {
        return requireTenant();
    }

    private static UUID requireTenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }

    static ApiException versionConflict() {
        return ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The subscription changed since it was read.");
    }

    private record ContractedPrice(BigDecimal amount, String currency) {}
}
