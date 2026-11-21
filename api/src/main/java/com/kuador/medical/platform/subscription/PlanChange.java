package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.api.PlanChangeKind;
import com.kuador.medical.platform.plan.BillingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A plan change of a clinic (plan D2, D3): applied at once (upgrade, or any change in TRIAL or
 * PAST_DUE) or scheduled for the renewal (downgrade or billing period change of an ACTIVE
 * subscription). At most one is SCHEDULED; a new request cancels it. Tenant-scoped (ADR-007).
 */
@Entity
@Table(name = "plan_change")
class PlanChange {

    /** {@code PlanChangeStatus} in {@code platform.openapi.yaml}. */
    enum Status {
        SCHEDULED,
        APPLIED,
        CANCELLED
    }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PlanChangeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "from_plan_id", nullable = false, updatable = false)
    private UUID fromPlanId;

    @Column(name = "to_plan_id", nullable = false, updatable = false)
    private UUID toPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_billing_period", nullable = false, updatable = false)
    private BillingPeriod fromBillingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, updatable = false)
    private BillingPeriod billingPeriod;

    @Column(name = "requested_by", updatable = false)
    private @Nullable UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "applied_at")
    private @Nullable Instant appliedAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    @Column(name = "prorated_amount", updatable = false)
    private @Nullable BigDecimal proratedAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    protected PlanChange() {}

    PlanChange(@Nullable UUID id, UUID subscriptionId, PlanChangeKind kind, UUID fromPlanId, UUID toPlanId,
            BillingPeriod fromBillingPeriod, BillingPeriod billingPeriod, @Nullable UUID requestedBy,
            Instant requestedAt, Instant effectiveAt, @Nullable BigDecimal proratedAmount, String currency) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.subscriptionId = subscriptionId;
        this.kind = kind;
        this.status = Status.SCHEDULED;
        this.fromPlanId = fromPlanId;
        this.toPlanId = toPlanId;
        this.fromBillingPeriod = fromBillingPeriod;
        this.billingPeriod = billingPeriod;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.effectiveAt = effectiveAt;
        this.proratedAmount = proratedAmount;
        this.currency = currency;
    }

    void markApplied(Instant at) {
        this.status = Status.APPLIED;
        this.appliedAt = at;
    }

    void cancel(Instant at) {
        this.status = Status.CANCELLED;
        this.cancelledAt = at;
    }

    UUID getId() {
        return id;
    }

    UUID getSubscriptionId() {
        return subscriptionId;
    }

    PlanChangeKind getKind() {
        return kind;
    }

    Status getStatus() {
        return status;
    }

    UUID getFromPlanId() {
        return fromPlanId;
    }

    UUID getToPlanId() {
        return toPlanId;
    }

    BillingPeriod getFromBillingPeriod() {
        return fromBillingPeriod;
    }

    BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    Instant getEffectiveAt() {
        return effectiveAt;
    }

    @Nullable BigDecimal getProratedAmount() {
        return proratedAmount;
    }

    String getCurrency() {
        return currency;
    }
}
