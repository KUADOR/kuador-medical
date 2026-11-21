package com.kuador.medical.platform.api;

import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.platform.plan.ModuleCode;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Capabilities of the plan of a clinic, checked in the backend on every operation (ADR-004,
 * ADR-017, Constitution VII). A clinic without a subscription (created before the platform
 * onboarding existed) has full access, every module and no limit.
 */
public interface PlanCapabilities {

    /** Whether the plan of the clinic enables {@code module}. */
    boolean isModuleEnabled(UUID tenantId, ModuleCode module);

    /** The value of {@code limit} in the plan of the clinic; empty when the plan does not limit it. */
    OptionalLong limit(UUID tenantId, LimitCode limit);

    /**
     * Checks that the clinic can create one more resource counted by {@code limit}: its current usage,
     * reported by the {@link PlanUsageProvider} of the owning module, must be below the plan value.
     * Usage above the limit (after a downgrade) never deletes anything (plan D11); it only blocks
     * new resources of that kind.
     *
     * @throws com.kuador.medical.shared.web.ApiException 409 {@code PLAN_LIMIT_REACHED}
     */
    void assertCanCreate(UUID tenantId, LimitCode limit);

    /** Access mode of the clinic according to the status of its subscription. */
    AccessMode accessMode(UUID tenantId);
}
