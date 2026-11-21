/**
 * Platform: tenants, clinic self-signup, system status, plans with their versioned prices and
 * the platform settings (MEDK-65), clinic onboarding by the SUPER_ADMIN and the initial
 * subscription (MEDK-66), and the subscription lifecycle with its reactivation (MEDK-68). Other
 * modules use the named interface {@code subscription}: the inbound payment events
 * ({@code PaymentReceived}, {@code PaymentFailed}) and {@code SubscriptionStatusChanged}.
 */
@ApplicationModule(displayName = "Platform")
package com.kuador.medical.platform;

import org.springframework.modulith.ApplicationModule;
