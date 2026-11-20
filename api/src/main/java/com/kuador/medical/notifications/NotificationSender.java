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

    /**
     * Whether text messages can be sent. False until an SMS provider is defined ([POR DEFINIR:
     * proveedor de SMS]); callers then keep the message pending instead of sending it.
     */
    default boolean smsAvailable() {
        return false;
    }

    /**
     * Sends a text message now. Inactive until an SMS provider adapter is added.
     *
     * @throws NotificationException when no SMS provider is configured or it rejects the message
     */
    default void sendSms(SmsMessage message) {
        throw new NotificationException("No SMS provider is configured");
    }
}
