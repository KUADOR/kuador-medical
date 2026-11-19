package com.kuador.medical;

import com.kuador.medical.identity.PlanLimits;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double of the plan limits (Constitution VII): no limit unless a test sets one for its
 * clinic. Registered by {@link TestcontainersConfiguration}.
 */
public class AdjustablePlanLimits implements PlanLimits {

    private final Map<UUID, Integer> userLimits = new ConcurrentHashMap<>();

    @Override
    public OptionalInt userLimit(UUID tenantId) {
        Integer limit = userLimits.get(tenantId);
        return limit == null ? OptionalInt.empty() : OptionalInt.of(limit);
    }

    public void limitUsers(UUID tenantId, int limit) {
        userLimits.put(tenantId, limit);
    }
}
