package com.kuador.medical.platform.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Immutable price version of a plan for one billing period (ADR-015), without VAT. A new price
 * is a new row; existing rows are never changed, so the history of contracted prices holds.
 */
@Entity
@Immutable
@Table(name = "plan_price")
class PlanPrice {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by")
    private @Nullable UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanPrice() {}

    PlanPrice(UUID id, UUID planId, BillingPeriod billingPeriod, String currency, BigDecimal amount,
            Instant effectiveFrom, @Nullable UUID createdBy, Instant createdAt) {
        this.id = id;
        this.planId = planId;
        this.billingPeriod = billingPeriod;
        this.currency = currency;
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getPlanId() {
        return planId;
    }

    BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    String getCurrency() {
        return currency;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
