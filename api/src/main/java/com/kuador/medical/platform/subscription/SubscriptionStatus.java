package com.kuador.medical.platform.subscription;

/**
 * Status of a subscription (ADR-016): {@code TRIAL}, {@code ACTIVE} (ACTIVA), {@code PAST_DUE}
 * (VENCIDA, grace period), {@code SUSPENDED} (SUSPENDIDA) and {@code CANCELLED} (CANCELADA).
 */
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELLED
}
