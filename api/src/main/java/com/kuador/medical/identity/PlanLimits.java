package com.kuador.medical.identity;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Port to the limits of the plan of a clinic (ADR-004, Constitution VII), implemented by the
 * platform module so that identity does not depend on it. The backend checks them on every
 * invitation or reactivation; clients are only informed.
 */
public interface PlanLimits {

    /** Maximum number of staff users (invited or active) of the clinic; empty when the plan sets none. */
    OptionalInt userLimit(UUID tenantId);
}
