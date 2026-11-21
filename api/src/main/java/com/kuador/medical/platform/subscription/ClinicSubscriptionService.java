package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.settings.PlatformSettingsService;
import com.kuador.medical.platform.subscription.SubscriptionResponses.ClinicSubscriptionResponse;
import com.kuador.medical.platform.subscription.SubscriptionResponses.LimitUsage;
import com.kuador.medical.platform.subscription.SubscriptionResponses.Notice;
import com.kuador.medical.platform.subscription.SubscriptionResponses.NoticeKind;
import com.kuador.medical.platform.subscription.SubscriptionResponses.PlanChangeResponse;
import com.kuador.medical.platform.subscription.SubscriptionResponses.PlanSummary;
import com.kuador.medical.platform.tenant.TenantRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /tenant/subscription}: the clinic's own view of its subscription (MEDK-69): status,
 * access mode, plan, enabled modules, every limit with its current usage, the scheduled plan
 * change and the notices to show. The clinic is the one of the access token (ADR-007); it never
 * sees another clinic nor the history that only the SUPER_ADMIN manages.
 */
@Service
class ClinicSubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionHistoryRepository history;
    private final PlanChangeService planChanges;
    private final SubscriptionCapabilities capabilities;
    private final PlanCatalog plans;
    private final PlatformSettingsService settings;
    private final TenantRepository tenants;
    private final Clock clock = Clock.systemUTC();

    ClinicSubscriptionService(
            SubscriptionRepository subscriptions,
            SubscriptionHistoryRepository history,
            PlanChangeService planChanges,
            SubscriptionCapabilities capabilities,
            PlanCatalog plans,
            PlatformSettingsService settings,
            TenantRepository tenants) {
        this.subscriptions = subscriptions;
        this.history = history;
        this.planChanges = planChanges;
        this.capabilities = capabilities;
        this.plans = plans;
        this.settings = settings;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    ClinicSubscriptionResponse current() {
        UUID tenantId = TenantContext.current().orElseThrow(ApiException::unauthenticated);
        Subscription subscription = subscriptions.findCurrent().orElseThrow(ApiException::notFound);
        boolean purged = tenants.findById(tenantId).map(tenant -> tenant.getPurgedAt() != null).orElse(false);
        Map<UUID, OfferedPlan> cache = new HashMap<>();
        OfferedPlan plan = plan(subscription.getPlanId(), cache);
        Optional<PlanChange> scheduled = planChanges.scheduled(subscription.getId());
        Optional<OfferedPlan> target = scheduled.map(change -> plan(change.getToPlanId(), cache));
        Instant now = clock.instant();

        List<LimitUsage> limits = new ArrayList<>();
        boolean exceedsNow = false;
        boolean exceedsTarget = false;
        for (LimitCode limit : LimitCode.values()) {
            Integer value = plan.limits().get(limit);
            OptionalLong usage = capabilities.usage(limit);
            limits.add(new LimitUsage(limit, value == null ? null : value.longValue(),
                    usage.isPresent() ? usage.getAsLong() : null));
            exceedsNow |= exceeds(value, usage);
            exceedsTarget |= target.map(targetPlan -> exceeds(targetPlan.limits().get(limit), usage)).orElse(false);
        }

        String currency = subscription.getPriceCurrencySnapshot() != null ? subscription.getPriceCurrencySnapshot()
                : plan.currentPrices().stream().map(PlanCatalog.CurrentPrice::currency).findFirst().orElse("USD");
        return new ClinicSubscriptionResponse(
                subscription.getStatus(),
                AccessMode.of(subscription.getStatus(), purged),
                new PlanSummary(plan.id(), plan.code(), plan.name()),
                subscription.getBillingPeriod(),
                subscription.getPriceAmountSnapshot() == null ? null
                        : SubscriptionResponses.money(subscription.getPriceAmountSnapshot()),
                currency,
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndsAt(),
                subscription.getGraceEndsAt(),
                subscription.getRetentionEndsAt(),
                List.copyOf(plan.modules()),
                limits,
                scheduled.map(change -> SubscriptionAdminService.planChangeView(change, id -> plan(id, cache))).orElse(null),
                notices(subscription, scheduled, exceedsNow, exceedsTarget, now));
    }

    private List<Notice> notices(Subscription subscription, Optional<PlanChange> scheduled, boolean exceedsNow,
            boolean exceedsTarget, Instant now) {
        List<Notice> notices = new ArrayList<>();
        switch (subscription.getStatus()) {
            case TRIAL -> notices.add(new Notice(NoticeKind.TRIAL_ENDING, subscription.getTrialEndsAt()));
            case PAST_DUE -> notices.add(new Notice(NoticeKind.PAYMENT_OVERDUE, subscription.getGraceEndsAt()));
            case SUSPENDED -> notices.add(new Notice(NoticeKind.SUSPENDED, subscription.getSuspendedAt() == null ? null
                    : LifecycleDates.daysAfter(subscription.getSuspendedAt(),
                            settings.current().suspensionDaysBeforeCancel())));
            case CANCELLED -> notices.add(new Notice(NoticeKind.CANCELLED, subscription.getRetentionEndsAt()));
            case ACTIVE -> {}
        }
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE || subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            history.upcomingNotices(subscription.getId(), now).stream().findFirst()
                    .ifPresent(notice -> notices.add(new Notice(NoticeKind.PRICE_CHANGE, notice.getAppliesFrom())));
        }
        if (exceedsTarget) {
            notices.add(new Notice(NoticeKind.DOWNGRADE_EXCESS, scheduled.map(PlanChange::getEffectiveAt).orElse(null)));
        } else if (exceedsNow) {
            notices.add(new Notice(NoticeKind.DOWNGRADE_EXCESS, null));
        }
        return notices;
    }

    private static boolean exceeds(Integer limit, OptionalLong usage) {
        return limit != null && usage.isPresent() && usage.getAsLong() > limit;
    }

    private OfferedPlan plan(UUID planId, Map<UUID, OfferedPlan> cache) {
        return cache.computeIfAbsent(planId, id -> plans.find(id).orElseThrow(ApiException::notFound));
    }
}
