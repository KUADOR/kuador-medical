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
 * the clinic bound in the tenant context. Transitions belong to the lifecycle (MEDK-68).
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
}
