/**
 * Identity: platform and clinic accounts (one per clinic, ADR-012), staff users and invitations,
 * authentication, permission catalog and clinic roles.
 * Public API: {@link com.kuador.medical.identity.AccountProvisioning},
 * {@link com.kuador.medical.identity.CurrentUser}, {@link com.kuador.medical.identity.Permission}
 * and the {@link com.kuador.medical.identity.TenantDirectory} and {@link com.kuador.medical.identity.PlanLimits}
 * ports implemented by the platform module.
 */
@ApplicationModule(displayName = "Identity")
package com.kuador.medical.identity;

import org.springframework.modulith.ApplicationModule;
