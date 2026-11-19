package com.kuador.medical.notifications.internal;

import com.kuador.medical.notifications.NotificationSender;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/** SMTP when {@code spring.mail.host} is set, otherwise a sender that drops the emails. */
@Configuration(proxyBeanMethods = false)
class NotificationConfiguration {

    @Bean
    NotificationSender notificationSender(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${kuador.mail.from:}") String from) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || !StringUtils.hasText(host)) {
            return new DisabledNotificationSender();
        }
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        return new SmtpNotificationSender(sender, from, executor);
    }
}
