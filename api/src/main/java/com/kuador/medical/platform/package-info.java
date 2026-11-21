/**
 * Platform: tenants, clinic self-signup, system status, plans with their versioned prices and
 * the platform settings (MEDK-65), clinic onboarding by the SUPER_ADMIN and the initial
 * subscription (MEDK-66), the subscription lifecycle with its reactivation (MEDK-68), and plan
 * changes, capabilities and the read-only mode (MEDK-69). Other modules use the named interfaces
 * {@code subscription} (the inbound payment events {@code PaymentReceived} and {@code PaymentFailed},
 * and {@code SubscriptionStatusChanged}), {@code api} ({@code PlanCapabilities}, the
 * {@code PlanUsageProvider} SPI and the plan change events) and {@code catalog} ({@code ModuleCode},
 * {@code LimitCode}, {@code BillingPeriod}).
 */
@ApplicationModule(displayName = "Platform")
package com.kuador.medical.platform;

import org.springframework.modulith.ApplicationModule;
