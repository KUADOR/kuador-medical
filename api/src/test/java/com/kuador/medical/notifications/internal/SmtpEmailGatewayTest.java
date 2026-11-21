package com.kuador.medical.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.kuador.medical.notifications.EmailGateway;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * The SMTP adapter against an in-process SMTP server, configured only through environment
 * properties (spring.mail.*, kuador.mail.from), as in every environment.
 */
@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailGatewayTest {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    private static final String RECIPIENT = "owner@clinic.example.test";
    private static final EmailMessage MESSAGE = new EmailMessage(
            RECIPIENT, "Activa tu cuenta de Clínica Ñandú", "Hola Ana:\n\nEnlace: https://web.example.test/#t=secret-token\n");

    /** Output without the lines of the test SMTP server itself, which logs the users it creates. */
    private static String applicationLogs(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> !line.contains("com.icegreen.greenmail"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private ApplicationContextRunner configuredWith(String... properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(SmtpEmailGateway.class)
                .withPropertyValues(properties);
    }

    private ApplicationContextRunner configuredSmtp() {
        return configuredWith(
                "spring.mail.host=127.0.0.1",
                "spring.mail.port=" + SMTP.getSmtp().getPort(),
                "kuador.mail.from=no-reply@medical.example.test");
    }

    @Test
    void sendsAPlainTextUtf8EmailFromTheConfiguredSender() throws Exception {
        configuredSmtp().run(context -> context.getBean(EmailGateway.class).sendEmail(MESSAGE));

        MimeMessage[] received = SMTP.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage mail = received[0];
        assertThat(mail.getAllRecipients()).extracting(Object::toString).containsExactly(RECIPIENT);
        assertThat(mail.getFrom()).extracting(Object::toString).containsExactly("no-reply@medical.example.test");
        assertThat(mail.getSubject()).isEqualTo("Activa tu cuenta de Clínica Ñandú");
        assertThat(mail.getContentType()).startsWith("text/plain").containsIgnoringCase("UTF-8");
        assertThat(GreenMailUtil.getBody(mail)).contains("https://web.example.test/#t=secret-token");
    }

    @Test
    void refusesToSendWithoutSmtpHost() {
        configuredWith("kuador.mail.from=no-reply@medical.example.test").run(context ->
                assertThatThrownBy(() -> context.getBean(EmailGateway.class).sendEmail(MESSAGE))
                        .isInstanceOf(NotificationException.class)
                        .hasMessageContaining("SMTP is not configured"));
        assertThat(SMTP.getReceivedMessages()).isEmpty();
    }

    @Test
    void refusesToSendWithoutSenderAddress() {
        configuredWith("spring.mail.host=127.0.0.1", "spring.mail.port=" + SMTP.getSmtp().getPort()).run(context ->
                assertThatThrownBy(() -> context.getBean(EmailGateway.class).sendEmail(MESSAGE))
                        .isInstanceOf(NotificationException.class)
                        .hasMessageContaining("SMTP is not configured"));
        assertThat(SMTP.getReceivedMessages()).isEmpty();
    }

    @Test
    void anInvalidRecipientFailsWithoutExposingTheAddress(CapturedOutput output) {
        String invalid = "<owner@clinic.example.test";
        configuredSmtp().run(context ->
                assertThatThrownBy(() -> context.getBean(EmailGateway.class)
                                .sendEmail(new EmailMessage(invalid, "Asunto", "Cuerpo")))
                        .isInstanceOf(NotificationException.class)
                        .hasNoCause()
                        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("clinic.example.test")));
        assertThat(applicationLogs(output)).doesNotContain("clinic.example.test");
    }

    @Test
    void anUnreachableServerFailsWithoutExposingTheMessage(CapturedOutput output) {
        configuredWith("spring.mail.host=127.0.0.1", "spring.mail.port=1", "kuador.mail.from=no-reply@medical.example.test")
                .run(context -> assertThatThrownBy(() -> context.getBean(EmailGateway.class).sendEmail(MESSAGE))
                        .isInstanceOf(NotificationException.class)
                        .hasNoCause()
                        .satisfies(error -> assertThat(error.getMessage())
                                .doesNotContain(RECIPIENT)
                                .doesNotContain("secret-token")));
        assertThat(applicationLogs(output)).doesNotContain(RECIPIENT).doesNotContain("secret-token").doesNotContain("Ñandú");
    }

    @Test
    void sendingDoesNotLogTheRecipientNorTheContent(CapturedOutput output) {
        configuredSmtp().run(context -> context.getBean(EmailGateway.class).sendEmail(MESSAGE));

        assertThat(SMTP.getReceivedMessages()).hasSize(1);
        assertThat(applicationLogs(output)).doesNotContain(RECIPIENT).doesNotContain("secret-token").doesNotContain("Ñandú");
    }
}
