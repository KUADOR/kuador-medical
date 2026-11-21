package com.kuador.medical.platform.subscription;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.idempotency.PlatformIdempotentCreations;
import com.kuador.medical.platform.subscription.SubscriptionRequests.PlanChangeRequest;
import com.kuador.medical.platform.subscription.SubscriptionRequests.PriceLockRequest;
import com.kuador.medical.platform.subscription.SubscriptionRequests.TransitionRequest;
import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.subscription.SubscriptionResponses.PeriodResponse;
import com.kuador.medical.platform.subscription.SubscriptionResponses.PlanChangePreviewResponse;
import com.kuador.medical.platform.subscription.SubscriptionResponses.PlanChangeResponse;
import com.kuador.medical.platform.subscription.SubscriptionResponses.PlanSummary;
import com.kuador.medical.platform.subscription.SubscriptionResponses.SubscriptionResponse;
import com.kuador.medical.platform.subscription.SubscriptionResponses.TransitionResponse;
import com.kuador.medical.platform.subscription.SubscriptionTransition.Actor;
import com.kuador.medical.platform.tenant.TenantRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Subscription of a clinic as the SUPER_ADMIN manages it (ADR-016): read, manual transitions
 * (including the reactivation from CANCELLED, plan D12) and price lock. The clinic is bound in the
 * tenant context for each call (ADR-007); clinics that were not onboarded by the platform are not
 * found.
 */
@Service
class SubscriptionAdminService {

    static final String PLAN_CHANGE_OPERATION = "requestPlanChange";

    private final SubscriptionLifecycle lifecycle;
    private final SubscriptionHistoryRepository history;
    private final PlanCatalog plans;
    private final TenantRepository tenants;
    private final PlanChangeService planChanges;
    private final PlatformIdempotentCreations idempotency;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock = Clock.systemUTC();

    SubscriptionAdminService(
            SubscriptionLifecycle lifecycle,
            SubscriptionHistoryRepository history,
            PlanCatalog plans,
            TenantRepository tenants,
            PlanChangeService planChanges,
            PlatformIdempotentCreations idempotency,
            PlatformTransactionManager transactionManager) {
        this.lifecycle = lifecycle;
        this.history = history;
        this.plans = plans;
        this.tenants = tenants;
        this.planChanges = planChanges;
        this.idempotency = idempotency;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    SubscriptionResponse get(UUID tenantId) {
        return inClinic(tenantId, readOnly, () -> view(lifecycle.current()));
    }

    SubscriptionResponse transition(UUID tenantId, TransitionRequest request, AuthenticatedAccount actor) {
        return inClinic(tenantId, transaction, () -> view(lifecycle.transition(request.target(), request.reason(),
                request.version(), new Actor(actor.accountId(), actor.displayName()), clock.instant())));
    }

    SubscriptionResponse lockPrice(UUID tenantId, PriceLockRequest request, AuthenticatedAccount actor) {
        LocalDate lockedUntil = request.lockedUntil();
        if (lockedUntil != null && lockedUntil.isBefore(LocalDate.now(clock.withZone(LifecycleDates.ZONE)))) {
            throw ApiException.validation(List.of(new FieldViolation("lockedUntil", "IN_THE_PAST")));
        }
        return inClinic(tenantId, transaction, () -> view(
                lifecycle.lockPrice(lockedUntil, request.version(), actor.accountId(), clock.instant())));
    }

    /** {@code POST .../plan-change-preview}: nothing changes. */
    PlanChangePreviewResponse previewPlanChange(UUID tenantId, PlanChangeRequest request) {
        return inClinic(tenantId, readOnly, () -> {
            PlanChangeService.Evaluation evaluation =
                    planChanges.preview(request.targetPlanId(), request.billingPeriod(), clock.instant());
            OfferedPlan target = evaluation.targetPlan();
            return new PlanChangePreviewResponse(
                    evaluation.kind(),
                    new PlanSummary(target.id(), target.code(), target.name()),
                    evaluation.billingPeriod(),
                    evaluation.effectiveAt(),
                    evaluation.proratedAmount() == null ? null : SubscriptionResponses.money(evaluation.proratedAmount()),
                    evaluation.targetPrice().currency(),
                    SubscriptionResponses.money(evaluation.targetPrice().amount()),
                    evaluation.excess(),
                    evaluation.disabledModules());
        });
    }

    /** {@code POST .../plan-changes}: applied or scheduled, idempotent by {@code Idempotency-Key}. */
    PlanChangeResponse requestPlanChange(UUID tenantId, PlanChangeRequest request, @Nullable String idempotencyKey,
            AuthenticatedAccount actor) {
        return inClinic(tenantId, transaction, () -> {
            UUID changeId = idempotency.create(actor.accountId(), PLAN_CHANGE_OPERATION + ":" + tenantId, idempotencyKey,
                    request, () -> planChanges.request(request.id(), request.targetPlanId(), request.billingPeriod(),
                            request.version(), actor.accountId(), clock.instant()).getId()).resourceId();
            return changeView(planChanges.find(changeId).orElseThrow(ApiException::notFound), new HashMap<>());
        });
    }

    private <T> T inClinic(UUID tenantId, TransactionTemplate template, Supplier<T> work) {
        if (!tenants.existsOnboarded(tenantId)) {
            throw ApiException.notFound();
        }
        return TenantContext.callAs(tenantId, () -> Objects.requireNonNull(template.execute(status -> work.get())));
    }

    private SubscriptionResponse view(Subscription subscription) {
        Instant now = clock.instant();
        boolean purged = lifecycle.purged();
        boolean reactivable = ReactivationPolicy.reactivable(
                subscription.getStatus(), subscription.getRetentionEndsAt(), purged, now);
        List<SubscriptionStatus> allowedTargets = purged ? List.of()
                : SubscriptionStateMachine.manualTargets(subscription.getStatus()).stream()
                        .filter(target -> subscription.getStatus() != SubscriptionStatus.CANCELLED || reactivable)
                        .toList();
        Map<UUID, OfferedPlan> planCache = new HashMap<>();
        OfferedPlan plan = plan(subscription.getPlanId(), planCache);
        String currency = subscription.getPriceCurrencySnapshot() != null ? subscription.getPriceCurrencySnapshot()
                : plan.currentPrices().stream().map(PlanCatalog.CurrentPrice::currency).findFirst().orElse("USD");
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                AccessMode.of(subscription.getStatus(), purged),
                new PlanSummary(plan.id(), plan.code(), plan.name()),
                subscription.getBillingPeriod(),
                subscription.getPriceAmountSnapshot() == null ? null
                        : SubscriptionResponses.money(subscription.getPriceAmountSnapshot()),
                currency,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndsAt(),
                subscription.getGraceEndsAt(),
                subscription.getSuspendedAt(),
                subscription.getCancelledAt(),
                subscription.getRetentionEndsAt(),
                subscription.getReactivatedAt(),
                SubscriptionLifecycle.lockedUntil(subscription),
                purged,
                reactivable,
                allowedTargets,
                planChanges.scheduled(subscription.getId()).map(change -> changeView(change, planCache)).orElse(null),
                history.periods(subscription.getId()).stream()
                        .map(period -> new PeriodResponse(plan(period.getPlanId(), planCache).name(),
                                period.getPeriodStart(), period.getPeriodEnd(),
                                SubscriptionResponses.money(period.getAmount()), period.getCurrency()))
                        .toList(),
                history.transitions(subscription.getId()).stream()
                        .map(transition -> new TransitionResponse(transition.getFromStatus(), transition.getToStatus(),
                                transition.getCause(), transition.getActorName(), transition.getReason(),
                                transition.getAt()))
                        .toList(),
                subscription.getVersion());
    }

    private PlanChangeResponse changeView(PlanChange change, Map<UUID, OfferedPlan> cache) {
        return planChangeView(change, id -> plan(id, cache));
    }

    static PlanChangeResponse planChangeView(PlanChange change, java.util.function.Function<UUID, OfferedPlan> plans) {
        OfferedPlan from = plans.apply(change.getFromPlanId());
        OfferedPlan to = plans.apply(change.getToPlanId());
        return new PlanChangeResponse(
                change.getId(),
                change.getKind(),
                change.getStatus(),
                new PlanSummary(from.id(), from.code(), from.name()),
                new PlanSummary(to.id(), to.code(), to.name()),
                change.getBillingPeriod(),
                change.getRequestedAt(),
                change.getEffectiveAt(),
                change.getProratedAmount() == null ? null : SubscriptionResponses.money(change.getProratedAmount()),
                change.getCurrency());
    }

    private OfferedPlan plan(UUID planId, Map<UUID, OfferedPlan> cache) {
        return cache.computeIfAbsent(planId, id -> plans.find(id).orElseThrow(ApiException::notFound));
    }
}
