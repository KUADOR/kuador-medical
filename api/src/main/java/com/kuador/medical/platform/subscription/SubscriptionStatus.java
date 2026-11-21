package com.kuador.medical.platform.subscription;

import org.springframework.modulith.NamedInterface;

/**
 * Status of a subscription (ADR-016): {@code TRIAL}, {@code ACTIVE} (ACTIVA), {@code PAST_DUE}
 * (VENCIDA, grace period), {@code SUSPENDED} (SUSPENDIDA) and {@code CANCELLED} (CANCELADA).
 */
@NamedInterface("subscription")
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELLED
}
