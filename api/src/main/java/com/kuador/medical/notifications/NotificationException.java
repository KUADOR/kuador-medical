package com.kuador.medical.notifications;

/** The provider could not take the notification. Its message never contains the recipient or the body. */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
