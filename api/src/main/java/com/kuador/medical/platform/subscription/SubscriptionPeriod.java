package com.kuador.medical.platform.subscription;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Contracted price of one period of a subscription (ADR-015). Immutable; tenant-scoped (ADR-007). */
@Entity
@Table(name = "subscription_period")
class SubscriptionPeriod {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    /** Set by the database; orders periods that start on the same day. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected SubscriptionPeriod() {}

    SubscriptionPeriod(UUID subscriptionId, UUID planId, Instant periodStart, Instant periodEnd, BigDecimal amount,
            String currency) {
        this.id = UUID.randomUUID();
        this.subscriptionId = subscriptionId;
        this.planId = planId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.currency = currency;
    }

    UUID getPlanId() {
        return planId;
    }

    Instant getPeriodStart() {
        return periodStart;
    }

    Instant getPeriodEnd() {
        return periodEnd;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }
}
