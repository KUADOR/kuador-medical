package com.kuador.medical.platform.subscription;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.platform.api.PlanChangeApplied;
import com.kuador.medical.platform.api.PlanChangeKind;
import com.kuador.medical.platform.api.PlanChangeScheduled;
import com.kuador.medical.platform.api.PlanUpgraded;
import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.platform.plan.ModuleCode;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.CurrentPrice;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.subscription.SubscriptionResponses.LimitExcess;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan changes of a clinic (MEDK-69, plan D2 and D3, MEDK-49 CA 8 and 9). An upgrade of an ACTIVE
 * subscription applies at once: the contracted price becomes the new one and the proration of the
 * remaining days is recorded and published in {@link PlanUpgraded} for the billing epic. A
 * downgrade or a billing period change waits for the renewal ({@code currentPeriodEnd}), without
 * refund; the preview shows the usage above the target limits and the modules the clinic would
 * lose. Usage above a limit never deletes data: once the change applies, the clinic only cannot
 * create more resources of that kind ({@code PLAN_LIMIT_REACHED}). In TRIAL and PAST_DUE there is
 * no current period to wait for, so every change applies at once without proration. Each method
 * runs in the caller's transaction with the clinic bound in the tenant context (ADR-007).
 */
@Service
class PlanChangeService {

    private static final Logger log = LoggerFactory.getLogger(PlanChangeService.class);

    private final EntityManager entityManager;
    private final SubscriptionRepository subscriptions;
    private final PlanChangeRepository changes;
    private final PlanCatalog plans;
    private final SubscriptionCapabilities capabilities;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;

    PlanChangeService(
            EntityManager entityManager,
            SubscriptionRepository subscriptions,
            PlanChangeRepository changes,
            PlanCatalog plans,
            SubscriptionCapabilities capabilities,
            AuditRecorder audit,
            ApplicationEventPublisher events) {
        this.entityManager = entityManager;
        this.subscriptions = subscriptions;
        this.changes = changes;
        this.plans = plans;
        this.capabilities = capabilities;
        this.audit = audit;
        this.events = events;
    }

    /** What a change would do, computed without changing anything. */
    record Evaluation(
            PlanChangeKind kind,
            OfferedPlan targetPlan,
            CurrentPrice targetPrice,
            BillingPeriod billingPeriod,
            boolean appliesAtOnce,
            Instant effectiveAt,
            @Nullable BigDecimal proratedAmount,
            List<LimitExcess> excess,
            List<ModuleCode> disabledModules) {}

    /** {@code POST .../plan-change-preview}. */
    @Transactional(propagation = Propagation.MANDATORY)
    Evaluation preview(UUID targetPlanId, BillingPeriod billingPeriod, Instant now) {
        return evaluate(current(), targetPlanId, billingPeriod, now);
    }

    /**
     * {@code POST .../plan-changes}: applies or schedules the change as the SUPER_ADMIN
     * {@code actorId}. A change already scheduled is replaced (cancelled).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    PlanChange request(@Nullable UUID id, UUID targetPlanId, BillingPeriod billingPeriod, @Nullable Long version,
            UUID actorId, Instant now) {
        Subscription subscription = current();
        if (version != null && subscription.getVersion() != version) {
            throw SubscriptionLifecycle.versionConflict();
        }
        if (id != null && changes.existsById(id)) {
            throw ApiException.conflict(ErrorCode.RESOURCE_ID_TAKEN, "A plan change with this id already exists.");
        }
        Evaluation evaluation = evaluate(subscription, targetPlanId, billingPeriod, now);
        changes.findScheduled(subscription.getId()).ifPresent(scheduled -> cancel(scheduled, actorId, now));
        PlanChange change = changes.save(new PlanChange(id, subscription.getId(), evaluation.kind(),
                subscription.getPlanId(), targetPlanId, subscription.getBillingPeriod(), billingPeriod, actorId, now,
                evaluation.effectiveAt(), evaluation.proratedAmount(), evaluation.targetPrice().currency()));
        flush();
        if (evaluation.appliesAtOnce()) {
            apply(subscription, change, evaluation.targetPrice(), actorId, now);
        } else {
            audit.record(AuditAction.PLAN_CHANGE_SCHEDULED, tenantId(), actorId, AuditTarget.subscription(subscription.getId()));
            events.publishEvent(new PlanChangeScheduled(tenantId(), subscription.getId(), change.getId(), change.getKind(),
                    change.getFromPlanId(), change.getToPlanId(), change.getBillingPeriod(), change.getEffectiveAt()));
        }
        return change;
    }

    /** A plan change of the clinic bound in the tenant context. */
    @Transactional(readOnly = true)
    Optional<PlanChange> find(UUID id) {
        return changes.findById(id);
    }

    /** The change waiting for the renewal of the subscription, if any. */
    @Transactional(readOnly = true)
    Optional<PlanChange> scheduled(UUID subscriptionId) {
        return changes.findScheduled(subscriptionId);
    }

    /**
     * Applies the scheduled change whose renewal arrived: when a new period starts at or after its
     * {@code effectiveAt} ({@code at} being that start), or when the job reaches the end of the
     * period. If the target plan no longer has a price for the billing period the change is
     * cancelled and the subscription keeps its plan.
     *
     * @return whether the plan of the subscription changed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean applyDue(Subscription subscription, Instant at) {
        Optional<PlanChange> scheduled = changes.findScheduled(subscription.getId());
        if (scheduled.isEmpty() || scheduled.get().getEffectiveAt().isAfter(at)) {
            return false;
        }
        PlanChange change = scheduled.get();
        Optional<CurrentPrice> price = plans.find(change.getToPlanId()).flatMap(plan -> plan.priceFor(change.getBillingPeriod()));
        if (price.isEmpty()) {
            log.warn("Scheduled plan change {} of tenant {} cancelled: the target plan has no price for {}",
                    change.getId(), tenantId(), change.getBillingPeriod());
            cancel(change, null, at);
            return false;
        }
        apply(subscription, change, price.get(), null, at);
        return true;
    }

    // ------------------------------------------------------------------ internals

    private Evaluation evaluate(Subscription subscription, UUID targetPlanId, BillingPeriod billingPeriod, Instant now) {
        OfferedPlan target = plans.find(targetPlanId).orElseThrow(() -> ApiException.validation(
                List.of(new FieldViolation("targetPlanId", "NOT_FOUND"))));
        PlanChangeRules.checkRequest(subscription.getStatus(), subscription.getPlanId(), subscription.getBillingPeriod(),
                targetPlanId, billingPeriod);
        if (target.archived()) {
            throw ApiException.conflict(ErrorCode.PLAN_ARCHIVED, "The target plan is archived.");
        }
        if (target.trial()) {
            throw PlanChangeRules.notAllowed("The trial plan cannot be the target of a plan change.");
        }
        CurrentPrice targetPrice = target.priceFor(billingPeriod).orElseThrow(() -> ApiException.validation(
                List.of(new FieldViolation("billingPeriod", "PRICE_NOT_AVAILABLE"))));
        BigDecimal contracted = subscription.getPriceAmountSnapshot();
        PlanChangeKind kind = PlanChangeRules.kind(subscription.getBillingPeriod(), contracted, billingPeriod,
                targetPrice.amount());
        boolean atOnce = PlanChangeRules.appliesAtOnce(subscription.getStatus(), kind);
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        Instant effectiveAt = atOnce || periodEnd == null ? now : periodEnd;
        BigDecimal prorated = null;
        if (kind == PlanChangeKind.UPGRADE && contracted != null && subscription.getCurrentPeriodStart() != null
                && periodEnd != null && subscription.getStatus() != SubscriptionStatus.TRIAL) {
            prorated = ProrationCalculator.prorate(contracted, targetPrice.amount(),
                    subscription.getCurrentPeriodStart(), periodEnd, now);
        }
        OfferedPlan currentPlan = plans.find(subscription.getPlanId()).orElseThrow(ApiException::notFound);
        return new Evaluation(kind, target, targetPrice, billingPeriod, atOnce, effectiveAt, prorated,
                excess(target.limits()), disabledModules(currentPlan.modules(), target.modules()));
    }

    /** Current usage above the limits of the target plan; limits without a usage provider are unknown and left out. */
    private List<LimitExcess> excess(Map<LimitCode, Integer> targetLimits) {
        List<LimitExcess> excess = new ArrayList<>();
        for (LimitCode limit : LimitCode.values()) {
            Integer value = targetLimits.get(limit);
            if (value == null) {
                continue;
            }
            OptionalLong usage = capabilities.usage(limit);
            if (usage.isPresent() && usage.getAsLong() > value) {
                excess.add(new LimitExcess(limit, value, usage.getAsLong()));
            }
        }
        return excess;
    }

    private static List<ModuleCode> disabledModules(Set<ModuleCode> current, Set<ModuleCode> target) {
        Set<ModuleCode> disabled = current.isEmpty() ? EnumSet.noneOf(ModuleCode.class) : EnumSet.copyOf(current);
        disabled.removeAll(target);
        return List.copyOf(disabled);
    }

    private void apply(Subscription subscription, PlanChange change, CurrentPrice price, @Nullable UUID actorId,
            Instant at) {
        boolean trial = subscription.getStatus() == SubscriptionStatus.TRIAL;
        subscription.changePlan(change.getToPlanId(), change.getBillingPeriod(),
                trial ? null : price.amount(), trial ? null : price.currency(), at);
        change.markApplied(at);
        flush();
        UUID tenantId = tenantId();
        audit.record(AuditAction.PLAN_CHANGE_APPLIED, tenantId, actorId, AuditTarget.subscription(subscription.getId()));
        BigDecimal prorated = change.getProratedAmount();
        if (change.getKind() == PlanChangeKind.UPGRADE && prorated != null) {
            events.publishEvent(new PlanUpgraded(tenantId, subscription.getId(), change.getId(), change.getFromPlanId(),
                    change.getToPlanId(), change.getBillingPeriod(), prorated, change.getCurrency(), at));
        } else {
            events.publishEvent(new PlanChangeApplied(tenantId, subscription.getId(), change.getId(), change.getKind(),
                    change.getFromPlanId(), change.getToPlanId(), change.getBillingPeriod(), at));
        }
    }

    private void cancel(PlanChange change, @Nullable UUID actorId, Instant at) {
        change.cancel(at);
        flush();
        audit.record(AuditAction.PLAN_CHANGE_CANCELLED, tenantId(), actorId, AuditTarget.subscription(change.getSubscriptionId()));
    }

    private Subscription current() {
        return subscriptions.findCurrent().orElseThrow(ApiException::notFound);
    }

    private void flush() {
        try {
            entityManager.flush();
        } catch (OptimisticLockException e) {
            throw SubscriptionLifecycle.versionConflict();
        }
    }

    private static UUID tenantId() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }
}
