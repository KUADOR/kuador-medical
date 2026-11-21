/**
 * Public API of the platform for the other modules (ADR-017): {@link com.kuador.medical.platform.api.PlanCapabilities}
 * (modules, limits and access mode of the plan of a clinic), the
 * {@link com.kuador.medical.platform.api.PlanUsageProvider} SPI through which the module that owns a
 * resource reports its usage, and the plan change events (MEDK-69); the
 * {@link com.kuador.medical.platform.api.TenantDataExporter} and
 * {@link com.kuador.medical.platform.api.TenantDataPurger} SPIs through which each module exports and
 * purges its own data, and the purge events (MEDK-70, ADR-018).
 */
@NamedInterface("api")
package com.kuador.medical.platform.api;

import org.springframework.modulith.NamedInterface;
