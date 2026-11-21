package com.kuador.medical.notifications;

/**
 * Email channel of the notifications module (ADR-005). Modules send email only through this
 * interface; the provider lives in one adapter ({@code SmtpEmailGateway}, configured per
 * environment) and an architecture test forbids the mail SDK anywhere else.
 */
public interface EmailGateway {

    /**
     * Sends an email now. Callers that send inside a transaction should do it after commit.
     *
     * @throws NotificationException when the provider is not configured, rejects the message or
     *     cannot be reached; its message never contains the recipient, the subject nor the body
     */
    void sendEmail(EmailMessage message);
}
