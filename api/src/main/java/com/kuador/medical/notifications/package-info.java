/**
 * Notifications: outgoing email (and SMS later) with one adapter per provider (ADR-005). Public API:
 * {@link com.kuador.medical.notifications.EmailGateway} (email channel),
 * {@link com.kuador.medical.notifications.NotificationSender} (email plus SMS when available),
 * {@link com.kuador.medical.notifications.EmailMessage} and the Spanish templates of
 * {@link com.kuador.medical.notifications.PlatformEmails}.
 */
@ApplicationModule(displayName = "Notifications")
package com.kuador.medical.notifications;

import org.springframework.modulith.ApplicationModule;
