package com.kuador.medical.notifications;

/**
 * Sends notifications through the configured provider (ADR-005): no module depends on a provider
 * SDK. The SMTP adapter is the default; tests replace it with an in-memory double.
 */
public interface NotificationSender {

    /**
     * Sends an email now. Callers that send inside a transaction should do it after commit.
     *
     * @throws NotificationException when the provider rejects or cannot take the message
     */
    void sendEmail(EmailMessage message);
}
