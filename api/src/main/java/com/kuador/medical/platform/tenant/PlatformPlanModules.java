package com.kuador.medical.platform.tenant;

import com.kuador.medical.identity.PlanModules;
import com.kuador.medical.shared.capability.Capability;
import com.kuador.medical.shared.capability.CapabilityChecker;
import com.kuador.medical.shared.capability.CoreCapabilityChecker;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Modules enabled for a clinic, as the platform answers them. Plans do not exist yet (justified
 * exception of MEDK-1): every clinic gets the core capabilities, without the platform itself,
 * which no clinic operates. When the plans module lands this reads the plan of {@code tenantId}.
 */
@Component
class PlatformPlanModules implements PlanModules {

    private final CapabilityChecker capabilities;

    PlatformPlanModules(ObjectProvider<CapabilityChecker> capabilities) {
        this.capabilities = capabilities.getIfAvailable(CoreCapabilityChecker::new);
    }

    @Override
    public List<String> enabledModules(UUID tenantId) {
        return Arrays.stream(Capability.values())
                .filter(capability -> capability != Capability.PLATFORM)
                .filter(capabilities::isEnabled)
                .map(capability -> capability.name().toLowerCase(Locale.ROOT))
                .toList();
    }
}
