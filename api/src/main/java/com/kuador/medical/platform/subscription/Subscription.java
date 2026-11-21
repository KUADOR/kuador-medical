package com.kuador.medical.platform.subscription;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.kuador.medical.platform.plan.BillingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * The subscription of a clinic: its plan, the contracted price (snapshot, ADR-015) and the dates
 * of its status (ADR-016). One per clinic; tenant-scoped (ADR-007), so the platform reads it with
 * the clinic bound in the tenant context. Transitions go through {@link SubscriptionLifecycle} (MEDK-68).
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "price_amount_snapshot")
    private @Nullable BigDecimal priceAmountSnapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "price_currency_snapshot", length = 3)
    private @Nullable String priceCurrencySnapshot;

    @Column(name = "current_period_start")
    private @Nullable Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private @Nullable Instant currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private @Nullable Instant trialEndsAt;

    @Column(name = "grace_ends_at")
    private @Nullable Instant graceEndsAt;

    @Column(name = "suspended_at")
    private @Nullable Instant suspendedAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    @Column(name = "retention_ends_at")
    private @Nullable Instant retentionEndsAt;

    @Column(name = "reactivated_at")
    private @Nullable Instant reactivatedAt;

    @Column(name = "price_locked_until")
    private @Nullable Instant priceLockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Subscription() {}

    private Subscription(UUID planId, BillingPeriod billingPeriod, SubscriptionStatus status, Instant now) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.billingPeriod = billingPeriod;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Onboarding with the trial plan: TRIAL until {@code trialEndsAt}, without price. */
    static Subscription trial(UUID planId, BillingPeriod billingPeriod, Instant trialEndsAt, Instant now) {
        Subscription subscription = new Subscription(planId, billingPeriod, SubscriptionStatus.TRIAL, now);
        subscription.trialEndsAt = trialEndsAt;
        return subscription;
    }

    /** Onboarding with a paid plan: ACTIVE with a first period at the contracted price. */
    static Subscription active(UUID planId, BillingPeriod billingPeriod, BigDecimal amount, String currency,
            Instant periodStart, Instant periodEnd, Instant now) {
        Subscription subscription = new Subscription(planId, billingPeriod, SubscriptionStatus.ACTIVE, now);
        subscription.priceAmountSnapshot = amount;
        subscription.priceCurrencySnapshot = currency;
        subscription.currentPeriodStart = periodStart;
        subscription.currentPeriodEnd = periodEnd;
        return subscription;
    }

    // ------------------------------------------------------------------ lifecycle (MEDK-68)

    /** ACTIVE → PAST_DUE when the period ends unpaid; full access until {@code graceEndsAt}. */
    void markPastDue(Instant graceEndsAt, Instant now) {
        this.status = SubscriptionStatus.PAST_DUE;
        this.graceEndsAt = graceEndsAt;
        this.updatedAt = now;
    }

    /** Read-only and export from {@code suspendedAt}; data stays intact. */
    void suspend(Instant suspendedAt, Instant now) {
        this.status = SubscriptionStatus.SUSPENDED;
        this.suspendedAt = suspendedAt;
        this.updatedAt = now;
    }

    /** Cancelled at {@code cancelledAt}; read-only and export for the OWNER until {@code retentionEndsAt}. */
    void cancel(Instant cancelledAt, Instant retentionEndsAt, Instant now) {
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.retentionEndsAt = retentionEndsAt;
        this.updatedAt = now;
    }

    /**
     * ACTIVE with a new contracted period (ADR-015), from any status (renewal included). Leaving
     * SUSPENDED or CANCELLED is a reactivation; the dates of the previous statuses are cleared.
     */
    void activate(Instant periodStart, Instant periodEnd, BigDecimal amount, String currency, Instant now) {
        if (status == SubscriptionStatus.SUSPENDED || status == SubscriptionStatus.CANCELLED) {
            this.reactivatedAt = now;
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.priceAmountSnapshot = amount;
        this.priceCurrencySnapshot = currency;
        this.graceEndsAt = null;
        this.suspendedAt = null;
        this.cancelledAt = null;
        this.retentionEndsAt = null;
        this.updatedAt = now;
    }

    /** Sets ({@code lockedUntil}, the start of its last local day) or removes ({@code null}) the price lock. */
    void lockPrice(@Nullable Instant lockedUntil, Instant now) {
        this.priceLockedUntil = lockedUntil;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public @Nullable BigDecimal getPriceAmountSnapshot() {
        return priceAmountSnapshot;
    }

    public @Nullable String getPriceCurrencySnapshot() {
        return priceCurrencySnapshot;
    }

    public @Nullable Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public @Nullable Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public @Nullable Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public @Nullable Instant getGraceEndsAt() {
        return graceEndsAt;
    }

    public @Nullable Instant getSuspendedAt() {
        return suspendedAt;
    }

    public @Nullable Instant getCancelledAt() {
        return cancelledAt;
    }

    public @Nullable Instant getRetentionEndsAt() {
        return retentionEndsAt;
    }

    public @Nullable Instant getReactivatedAt() {
        return reactivatedAt;
    }

    public @Nullable Instant getPriceLockedUntil() {
        return priceLockedUntil;
    }

    public long getVersion() {
        return version;
    }
}
