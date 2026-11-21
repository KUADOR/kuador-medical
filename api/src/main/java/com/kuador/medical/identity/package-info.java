/**
 * Identity: platform and clinic accounts (one per clinic, ADR-012), staff users and invitations,
 * authentication with the mandatory TOTP second factor of the OWNER (ADR-019), permission catalog
 * and clinic roles.
 * Public API: {@link com.kuador.medical.identity.AccountProvisioning},
 * {@link com.kuador.medical.identity.CurrentUser}, {@link com.kuador.medical.identity.Permission},
 * {@link com.kuador.medical.identity.OwnerOnboarding} (OWNER invitation and second factor, MEDK-67)
 * and the {@link com.kuador.medical.identity.TenantDirectory}, {@link com.kuador.medical.identity.PlanLimits}, {@link com.kuador.medical.identity.StaffSeats} (usage of the user limit, MEDK-69)
 * and {@link com.kuador.medical.identity.OwnerInvitationPolicy} ports implemented by the platform module.
 */
@ApplicationModule(displayName = "Identity")
package com.kuador.medical.identity;

import org.springframework.modulith.ApplicationModule;
