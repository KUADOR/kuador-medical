package com.kuador.medical.notifications.internal;

import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP adapter of {@link NotificationSender} (Mailpit locally, provider [POR DEFINIR] elsewhere).
 * Sends on a virtual thread so the response time of the caller does not reveal whether an email
 * was sent (no account enumeration by timing). Logs never contain the address or the text.
 */
class SmtpNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final Executor executor;

    SmtpNotificationSender(JavaMailSender mailSender, String from, Executor executor) {
        this.mailSender = mailSender;
        this.from = from;
        this.executor = executor;
    }

    @Override
    public void sendEmail(EmailMessage message) {
        executor.execute(() -> send(message));
    }

    private void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        try {
            mailSender.send(mail);
        } catch (MailException | IllegalStateException e) {
            // Only the exception type: its message may carry the address.
            log.warn("Email could not be sent ({})", e.getClass().getName());
        }
    }
}
