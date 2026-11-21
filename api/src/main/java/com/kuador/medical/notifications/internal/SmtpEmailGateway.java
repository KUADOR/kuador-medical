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
 * SMTP adapter of {@link com.kuador.medical.notifications.EmailGateway} (ADR-005), configured per
 * environment through spring.mail.* ({@code KUADOR_SMTP_*}) and {@code KUADOR_MAIL_FROM}: Mailpit in
 * local, the provider of each environment elsewhere [POR DEFINIR: provider]. Without SMTP
 * configuration it refuses to send. It is the only class allowed to use the mail SDK.
 *
 * <p>It logs nothing, and its errors carry neither the recipient, the subject, the body nor the
 * provider exception (whose message may quote the addresses), so callers can log them safely.
 */
@Component
@EnableConfigurationProperties(MailProperties.class)
class SmtpEmailGateway implements NotificationSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties properties;

    SmtpEmailGateway(ObjectProvider<JavaMailSender> mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendEmail(EmailMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || properties.from().isBlank()) {
            throw new NotificationException("SMTP is not configured (KUADOR_SMTP_HOST, KUADOR_MAIL_FROM)");
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
            throw new NotificationException(
                    "The SMTP server did not accept the email (%s)".formatted(e.getClass().getSimpleName()));
        }
    }
}
