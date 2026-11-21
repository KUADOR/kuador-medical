package com.kuador.medical;

import com.kuador.medical.identity.PlanLimits;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double of the plan limits (Constitution VII): the limit a test sets for its clinic, or else
 * the one of the real plan of the clinic (MEDK-69). Registered by {@link TestcontainersConfiguration}.
 */
public class AdjustablePlanLimits implements PlanLimits {

    private final Map<UUID, Integer> userLimits = new ConcurrentHashMap<>();
    private final PlanLimits plan;

    public AdjustablePlanLimits(PlanLimits plan) {
        this.plan = plan;
    }

    @Override
    public OptionalInt userLimit(UUID tenantId) {
        Integer limit = userLimits.get(tenantId);
        return limit == null ? plan.userLimit(tenantId) : OptionalInt.of(limit);
    }

    public void limitUsers(UUID tenantId, int limit) {
        userLimits.put(tenantId, limit);
    }
}
