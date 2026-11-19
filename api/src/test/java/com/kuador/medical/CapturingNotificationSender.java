package com.kuador.medical;

import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double of {@link NotificationSender}: keeps the emails in memory instead of sending them,
 * so tests can read the links. Registered by {@link TestcontainersConfiguration}.
 */
public class CapturingNotificationSender implements NotificationSender {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmail(EmailMessage message) {
        sent.add(message);
    }

    /** Emails sent to {@code address}, oldest first. */
    public List<EmailMessage> sentTo(String address) {
        return sent.stream().filter(message -> message.to().equalsIgnoreCase(address)).toList();
    }
}
