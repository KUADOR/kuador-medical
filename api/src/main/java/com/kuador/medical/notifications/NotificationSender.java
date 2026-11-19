package com.kuador.medical.notifications;

/**
 * Sends notifications through the configured provider (ADR-005). Delivery is best effort and
 * asynchronous: a provider failure is logged without personal data and never reaches the caller,
 * so the answer of the operation that triggered it does not depend on the provider.
 */
public interface NotificationSender {

    void sendEmail(EmailMessage message);
}
