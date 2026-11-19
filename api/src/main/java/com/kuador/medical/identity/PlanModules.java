package com.kuador.medical.identity;

import java.util.List;
import java.util.UUID;

/**
 * Port to the platform (plans and capabilities, ADR-004), implemented by the platform module so
 * that identity does not depend on it. {@code GET /me} uses it only to tell the clients which
 * modules to show; the backend still checks every operation (Constitution VII).
 */
public interface PlanModules {

    /** Modules the plan of the clinic enables, for example {@code identity} or {@code patients}. */
    List<String> enabledModules(UUID tenantId);
}
