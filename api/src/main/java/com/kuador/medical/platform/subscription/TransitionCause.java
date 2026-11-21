package com.kuador.medical.platform.subscription;

import org.springframework.modulith.NamedInterface;

/** What moved a subscription to another status (ADR-016). */
@NamedInterface("subscription")
public enum TransitionCause {
    /** A SUPER_ADMIN, from the platform panel. */
    MANUAL,
    /** A {@code PaymentReceived} event of the billing epic. */
    PAYMENT_RECEIVED,
    /** A {@code PaymentFailed} event of the billing epic: recorded, never changes the status (plan D5). */
    PAYMENT_FAILED,
    /** The lifecycle job, when a date of the subscription is reached. */
    SCHEDULER
}
