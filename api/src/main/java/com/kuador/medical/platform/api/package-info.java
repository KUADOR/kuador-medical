/**
 * Public API of the platform for the other modules (ADR-017): {@link com.kuador.medical.platform.api.PlanCapabilities}
 * (modules, limits and access mode of the plan of a clinic), the
 * {@link com.kuador.medical.platform.api.PlanUsageProvider} SPI through which the module that owns a
 * resource reports its usage, and the plan change events (MEDK-69).
 */
@NamedInterface("api")
package com.kuador.medical.platform.api;

import org.springframework.modulith.NamedInterface;
