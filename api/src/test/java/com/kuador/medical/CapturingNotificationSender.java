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

    /**
     * Waits up to 5 seconds until {@code count} emails were sent to {@code address} (some are sent
     * in the background) and returns them, oldest first.
     */
    public List<EmailMessage> awaitSentTo(String address, int count) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (sentTo(address).size() < count && System.nanoTime() < deadline) {
            Thread.onSpinWait();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return sentTo(address);
    }

    /** Emails sent to {@code address} so far, oldest first. */
    public List<EmailMessage> sentTo(String address) {
        return sent.stream().filter(message -> message.to().equalsIgnoreCase(address)).toList();
    }
}
