package com.kuador.medical.notifications.internal;

import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationException;
import com.kuador.medical.notifications.NotificationSender;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP adapter (spring.mail.*, {@code KUADOR_SMTP_*}): Mailpit in local, the provider of each
 * environment elsewhere [POR DEFINIR: provider]. Without SMTP configuration it refuses to send.
 */
@Component
@EnableConfigurationProperties(MailProperties.class)
class SmtpNotificationSender implements NotificationSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties properties;

    SmtpNotificationSender(ObjectProvider<JavaMailSender> mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendEmail(EmailMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || properties.from().isBlank()) {
            throw new NotificationException("SMTP is not configured (KUADOR_SMTP_HOST, KUADOR_MAIL_FROM)", null);
        }
        try {
            var mime = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            sender.send(mime);
        } catch (MailException | jakarta.mail.MessagingException e) {
            throw new NotificationException("The SMTP server did not accept the email", e);
        }
    }
}
