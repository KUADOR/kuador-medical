package com.kuador.medical.platform.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * One status change of a subscription (ADR-016), with its cause, the SUPER_ADMIN of a manual one
 * and its reason. Immutable; tenant-scoped (ADR-007). A payment reference makes a delivered
 * {@code PaymentReceived} apply once.
 */
@Entity
@Table(name = "subscription_transition")
class SubscriptionTransition {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private @Nullable SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private SubscriptionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransitionCause cause;

    @Column(name = "actor_id", updatable = false)
    private @Nullable UUID actorId;

    @Column(name = "actor_name", updatable = false)
    private @Nullable String actorName;

    @Column(updatable = false)
    private @Nullable String reason;

    @Column(name = "payment_reference", updatable = false)
    private @Nullable String paymentReference;

    @Column(nullable = false, updatable = false)
    private Instant at;

    /** Set by the database; orders transitions recorded at the same instant. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected SubscriptionTransition() {}

    SubscriptionTransition(UUID subscriptionId, @Nullable SubscriptionStatus fromStatus, SubscriptionStatus toStatus,
            TransitionCause cause, @Nullable Actor actor, @Nullable String reason, @Nullable String paymentReference,
            Instant at) {
        this.id = UUID.randomUUID();
        this.subscriptionId = subscriptionId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.cause = cause;
        this.actorId = actor == null ? null : actor.id();
        this.actorName = actor == null ? null : actor.displayName();
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
        this.paymentReference = paymentReference;
        this.at = at;
    }

    @Nullable SubscriptionStatus getFromStatus() {
        return fromStatus;
    }

    SubscriptionStatus getToStatus() {
        return toStatus;
    }

    TransitionCause getCause() {
        return cause;
    }

    @Nullable String getActorName() {
        return actorName;
    }

    @Nullable String getReason() {
        return reason;
    }

    Instant getAt() {
        return at;
    }

    /** The SUPER_ADMIN of a manual transition. */
    record Actor(UUID id, String displayName) {}
}
