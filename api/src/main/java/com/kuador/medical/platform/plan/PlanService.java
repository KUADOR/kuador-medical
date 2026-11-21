package com.kuador.medical.platform.plan;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.platform.idempotency.PlatformIdempotentCreations;
import com.kuador.medical.platform.plan.PlanRequests.CreatePlan;
import com.kuador.medical.platform.plan.PlanRequests.CreatePrice;
import com.kuador.medical.platform.plan.PlanRequests.PriceInput;
import com.kuador.medical.platform.plan.PlanRequests.UpdatePlan;
import com.kuador.medical.platform.plan.PlanResponses.PlanResponse;
import com.kuador.medical.platform.plan.PlanResponses.PriceResponse;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans of Kuador and their versioned prices (MEDK-65, plan D9, ADR-015). Only the SUPER_ADMIN
 * reaches it (platform permissions). Every change is audited as a platform event (no tenant);
 * plans are archived, never deleted, and prices are immutable versions.
 */
@Service
class PlanService {

    static final String CREATE_PLAN_OPERATION = "platform.plans.create";
    /** Followed by the plan id, so a key is scoped to one plan (fits the 60 characters of the operation). */
    static final String CREATE_PRICE_OPERATION = "platform.plan-price:";

    private static final String CODE_UNIQUE = "plan_code_unique";
    private static final String SINGLE_TRIAL = "plan_single_trial_idx";
    private static final String PLAN_PRIMARY_KEY = "plan_pkey";
    private static final String PRICE_PRIMARY_KEY = "plan_price_pkey";

    private final PlanRepository plans;
    private final PlanPriceRepository prices;
    private final EntityManager entityManager;
    private final PlatformIdempotentCreations idempotency;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PlanService(
            PlanRepository plans,
            PlanPriceRepository prices,
            EntityManager entityManager,
            PlatformIdempotentCreations idempotency,
            AuditRecorder audit,
            ApplicationEventPublisher events) {
        this.plans = plans;
        this.prices = prices;
        this.entityManager = entityManager;
        this.idempotency = idempotency;
        this.audit = audit;
        this.events = events;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list(@Nullable PlanStatus status) {
        List<Plan> found = status == null ? plans.findAllOrdered() : plans.findByStatusOrdered(status);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PlanPrice>> byPlan = prices.findByPlanIds(found.stream().map(Plan::getId).toList()).stream()
                .collect(Collectors.groupingBy(PlanPrice::getPlanId));
        Instant now = clock.instant();
        return found.stream()
                .map(plan -> PlanResponse.of(plan, CurrentPrices.at(byPlan.getOrDefault(plan.getId(), List.of()), now)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public PlanResponse create(CreatePlan request, @Nullable String idempotencyKey, AuthenticatedAccount actor) {
        var outcome = idempotency.create(
                actor.accountId(), CREATE_PLAN_OPERATION, idempotencyKey, request, () -> doCreate(request, actor));
        return view(find(outcome.resourceId()));
    }

    private UUID doCreate(CreatePlan request, AuthenticatedAccount actor) {
        PlanValidation validation = new PlanValidation();
        Set<ModuleCode> modules = validation.modules(request.modules());
        Map<LimitCode, @Nullable Integer> limits = validation.limits(request.limits());
        List<PriceInput> initialPrices = request.prices() == null ? List.of() : request.prices();
        boolean trial = request.trial();
        if (trial && !initialPrices.isEmpty()) {
            validation.add("prices", "TRIAL_PLAN_HAS_NO_PRICES");
        }
        Set<BillingPeriod> periods = EnumSet.noneOf(BillingPeriod.class);
        List<String> currencies = new ArrayList<>();
        for (int i = 0; i < initialPrices.size(); i++) {
            PriceInput price = initialPrices.get(i);
            if (!periods.add(price.billingPeriod())) {
                validation.add("prices[" + i + "].billingPeriod", "DUPLICATE");
            }
            currencies.add(validation.currency("prices[" + i + "].currency", price.currency()));
        }
        validation.throwIfInvalid();

        List<FieldViolation> conflicts = new ArrayList<>();
        if (plans.existsByCode(request.code())) {
            conflicts.add(new FieldViolation("code", "DUPLICATE"));
        }
        if (trial && plans.existsTrialPlan()) {
            conflicts.add(new FieldViolation("trial", "DUPLICATE"));
        }
        if (!conflicts.isEmpty()) {
            throw ApiException.validationConflict(conflicts);
        }
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        if (plans.existsById(id)) {
            throw idTaken();
        }
        Instant now = clock.instant();
        Plan plan = new Plan(id, request.code(), request.name(), request.description(), trial, modules, limits, now);
        entityManager.persist(plan);
        for (int i = 0; i < initialPrices.size(); i++) {
            PriceInput input = initialPrices.get(i);
            entityManager.persist(new PlanPrice(UUID.randomUUID(), id, input.billingPeriod(), currencies.get(i),
                    validation.amount(input.amount()), now, actor.accountId(), now));
        }
        flush();
        audit.record(AuditAction.PLAN_CREATED, null, actor.accountId(), AuditTarget.plan(id));
        return id;
    }

    @Transactional
    public PlanResponse update(UUID id, UpdatePlan request, AuthenticatedAccount actor) {
        Plan plan = find(id);
        plan.ensureEditable();
        PlanValidation validation = new PlanValidation();
        Set<ModuleCode> modules = validation.modules(request.modules());
        Map<LimitCode, @Nullable Integer> limits = validation.limits(request.limits());
        validation.throwIfInvalid();
        plan.update(request.name(), request.description(), modules, limits, request.version(), clock.instant());
        flush();
        audit.record(AuditAction.PLAN_UPDATED, null, actor.accountId(), AuditTarget.plan(id));
        return view(plan);
    }

    @Transactional
    public PlanResponse archive(UUID id, AuthenticatedAccount actor) {
        Plan plan = find(id);
        if (plan.archive(clock.instant())) {
            flush();
            audit.record(AuditAction.PLAN_ARCHIVED, null, actor.accountId(), AuditTarget.plan(id));
        }
        return view(plan);
    }

    @Transactional(readOnly = true)
    public List<PriceResponse> priceHistory(UUID planId, @Nullable BillingPeriod billingPeriod) {
        find(planId);
        return prices.findHistory(planId).stream()
                .filter(price -> billingPeriod == null || price.getBillingPeriod() == billingPeriod)
                .map(PriceResponse::of)
                .toList();
    }

    @Transactional
    public PriceResponse createPrice(
            UUID planId, CreatePrice request, @Nullable String idempotencyKey, AuthenticatedAccount actor) {
        var outcome = idempotency.create(actor.accountId(), CREATE_PRICE_OPERATION + planId, idempotencyKey,
                request, () -> doCreatePrice(planId, request, actor));
        PlanPrice price = prices.findById(outcome.resourceId()).orElseThrow(ApiException::notFound);
        return PriceResponse.of(price);
    }

    private UUID doCreatePrice(UUID planId, CreatePrice request, AuthenticatedAccount actor) {
        Plan plan = find(planId);
        plan.ensureEditable();
        Instant now = clock.instant();
        PlanValidation validation = new PlanValidation();
        if (plan.isTrial()) {
            validation.add("billingPeriod", "TRIAL_PLAN_HAS_NO_PRICES");
        }
        String currency = validation.currency("currency", request.currency());
        validation.notInThePast("effectiveFrom", request.effectiveFrom(), now);
        validation.throwIfInvalid();
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        if (prices.existsById(id)) {
            throw idTaken();
        }
        PlanPrice price = new PlanPrice(id, planId, request.billingPeriod(), currency,
                validation.amount(request.amount()), request.effectiveFrom(), actor.accountId(), now);
        entityManager.persist(price);
        flush();
        audit.record(AuditAction.PLAN_PRICE_CREATED, null, actor.accountId(), AuditTarget.planPrice(id));
        events.publishEvent(new PlanPriceChanged(planId, id, request.billingPeriod(), request.effectiveFrom()));
        return id;
    }

    private Plan find(UUID id) {
        return plans.findById(id).orElseThrow(ApiException::notFound);
    }

    private PlanResponse view(Plan plan) {
        return PlanResponse.of(plan, CurrentPrices.at(prices.findHistory(plan.getId()), clock.instant()));
    }

    /** Races the pre-checks cannot see are decided by the database. */
    private void flush() {
        try {
            entityManager.flush();
        } catch (OptimisticLockException e) {
            throw Plan.versionConflict();
        } catch (PersistenceException e) {
            String constraint = violatedConstraint(e);
            if (CODE_UNIQUE.equals(constraint)) {
                throw ApiException.validationConflict(List.of(new FieldViolation("code", "DUPLICATE")));
            }
            if (SINGLE_TRIAL.equals(constraint)) {
                throw ApiException.validationConflict(List.of(new FieldViolation("trial", "DUPLICATE")));
            }
            if (PLAN_PRIMARY_KEY.equals(constraint) || PRICE_PRIMARY_KEY.equals(constraint)) {
                throw idTaken();
            }
            throw e;
        }
    }

    private static @Nullable String violatedConstraint(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    private static ApiException idTaken() {
        return ApiException.conflict(ErrorCode.RESOURCE_ID_TAKEN, "The id is already used.");
    }
}
