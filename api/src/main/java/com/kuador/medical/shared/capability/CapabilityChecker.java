package com.kuador.medical.shared.capability;

/**
 * Check point every operation goes through (ADR-004). It will answer from the plan of the
 * current tenant once the plans module exists.
 */
public interface CapabilityChecker {

    /** Whether the capability is enabled; {@code null} (unknown capability) is never enabled. */
    boolean isEnabled(Capability capability);
}
