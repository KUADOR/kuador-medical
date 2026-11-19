/**
 * Notifications: outgoing email (and SMS once a provider is chosen) behind a provider-neutral
 * interface (ADR-005). Public API: {@link com.kuador.medical.notifications.NotificationSender}
 * and {@link com.kuador.medical.notifications.EmailMessage}.
 */
@ApplicationModule(displayName = "Notifications")
package com.kuador.medical.notifications;

import org.springframework.modulith.ApplicationModule;
