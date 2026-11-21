package com.kuador.medical.platform.subscription;

import com.kuador.medical.identity.PlanLimits;
import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.api.PlanCapabilities;
import com.kuador.medical.platform.api.PlanUsageProvider;
import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.platform.plan.ModuleCode;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.tenant.TenantRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link PlanCapabilities} from the subscription of each clinic (ADR-017): its plan gives the
 * modules and limits and its status the access mode. Also the {@link PlanLimits} port of identity,
 * which checks the user limit when staff are invited or enabled.
 *
 * <p>A clinic without a subscription (signed up before the platform onboarding, MEDK-1) keeps the
 * behavior it had: full access, every module, and only the user limit of
 * {@code kuador.plans.user-limit} ({@code KUADOR_PLAN_USER_LIMIT}) when it is set.
 */
@Component
class SubscriptionCapabilities implements PlanCapabilities, PlanLimits {

    private final SubscriptionRepository subscriptions;
    private final PlanCatalog plans;
    private final TenantRepository tenants;
    private final Map<LimitCode, PlanUsageProvider> usageProviders = new EnumMap<>(LimitCode.class);
    private final TransactionTemplate readOnly;
    private final @Nullable Integer fallbackUserLimit;

    SubscriptionCapabilities(
            SubscriptionRepository subscriptions,
            PlanCatalog plans,
            TenantRepository tenants,
            List<PlanUsageProvider> usageProviders,
            PlatformTransactionManager transactionManager,
            @Value("${kuador.plans.user-limit:#{null}}") @Nullable Integer fallbackUserLimit) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.tenants = tenants;
        for (PlanUsageProvider provider : usageProviders) {
            if (this.usageProviders.put(provider.limit(), provider) != null) {
                throw new IllegalStateException("Two usage providers for " + provider.limit());
            }
        }
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.fallbackUserLimit = fallbackUserLimit == null || fallbackUserLimit <= 0 ? null : fallbackUserLimit;
    }

    /** What the capabilities of a clinic depend on: its plan and the status of its subscription. */
    record Contract(SubscriptionStatus status, OfferedPlan plan) {}

    @Override
    public boolean isModuleEnabled(UUID tenantId, ModuleCode module) {
        return contract(tenantId).map(contract -> contract.plan().modules().contains(module)).orElse(true);
    }

    @Override
    public OptionalLong limit(UUID tenantId, LimitCode limit) {
        Optional<Contract> contract = contract(tenantId);
        if (contract.isEmpty()) {
            return limit == LimitCode.MAX_USERS && fallbackUserLimit != null
                    ? OptionalLong.of(fallbackUserLimit) : OptionalLong.empty();
        }
        Integer value = contract.get().plan().limits().get(limit);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    @Override
    public void assertCanCreate(UUID tenantId, LimitCode limit) {
        OptionalLong value = limit(tenantId, limit);
        if (value.isEmpty()) {
            return;
        }
        OptionalLong usage = TenantContext.callAs(tenantId, () -> usage(limit));
        if (usage.isPresent() && usage.getAsLong() >= value.getAsLong()) {
            throw limitReached(limit, value.getAsLong());
        }
    }

    @Override
    public AccessMode accessMode(UUID tenantId) {
        return TenantContext.callAs(tenantId, () -> readOnly.execute(status -> {
            boolean purged = tenants.findById(tenantId).map(tenant -> tenant.getPurgedAt() != null).orElse(false);
            return subscriptions.findCurrent()
                    .map(subscription -> AccessMode.of(subscription.getStatus(), purged))
                    .orElse(purged ? AccessMode.NONE : AccessMode.FULL);
        }));
    }

    @Override
    public OptionalInt userLimit(UUID tenantId) {
        OptionalLong limit = limit(tenantId, LimitCode.MAX_USERS);
        return limit.isEmpty() ? OptionalInt.empty() : OptionalInt.of(Math.toIntExact(limit.getAsLong()));
    }

    /** Current usage of {@code limit} in the clinic bound in the tenant context; empty without a provider. */
    OptionalLong usage(LimitCode limit) {
        PlanUsageProvider provider = usageProviders.get(limit);
        return provider == null ? OptionalLong.empty() : OptionalLong.of(provider.currentUsage());
    }

    /** The plan and status of the clinic; empty when it has no subscription. */
    Optional<Contract> contract(UUID tenantId) {
        return TenantContext.callAs(tenantId, () -> readOnly.execute(status -> subscriptions.findCurrent()
                .map(subscription -> new Contract(subscription.getStatus(),
                        plans.find(subscription.getPlanId()).orElseThrow(
                                () -> new IllegalStateException("The plan of a subscription does not exist"))))));
    }

    static ApiException limitReached(LimitCode limit, long value) {
        return ApiException.conflict(ErrorCode.PLAN_LIMIT_REACHED,
                "The plan allows up to %d of %s.".formatted(value, limit.name()));
    }
}
