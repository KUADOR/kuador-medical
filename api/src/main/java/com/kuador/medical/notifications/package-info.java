/**
 * Notifications: outgoing email (and SMS later) behind {@link com.kuador.medical.notifications.NotificationSender},
 * with one adapter per provider (ADR-005). Public API: {@link com.kuador.medical.notifications.NotificationSender}
 * and {@link com.kuador.medical.notifications.EmailMessage}.
 */
@ApplicationModule(displayName = "Notifications")
package com.kuador.medical.notifications;

import org.springframework.modulith.ApplicationModule;
