package com.kuador.medical.shared.capability;

import java.util.EnumSet;
import java.util.Set;

/**
 * Justified exception to Constitution VII (plan of MEDK-1): plans do not exist yet, so only the
 * core capabilities, which belong to every plan, are enabled. Remove when the plans module lands.
 */
public final class CoreCapabilityChecker implements CapabilityChecker {

    private static final Set<Capability> CORE = EnumSet.of(Capability.PLATFORM, Capability.IDENTITY);

    @Override
    public boolean isEnabled(Capability capability) {
        return capability != null && CORE.contains(capability);
    }
}
