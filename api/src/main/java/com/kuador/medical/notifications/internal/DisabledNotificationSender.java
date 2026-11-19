package com.kuador.medical.notifications.internal;

import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no SMTP server is configured: emails are dropped and only that fact is logged. */
class DisabledNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledNotificationSender.class);

    @Override
    public void sendEmail(EmailMessage message) {
        log.warn("Email not sent: no SMTP server is configured (KUADOR_SMTP_HOST)");
    }
}
