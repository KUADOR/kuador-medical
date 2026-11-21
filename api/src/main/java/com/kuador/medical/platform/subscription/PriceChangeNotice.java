package com.kuador.medical.platform.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/**
 * The OWNER was told that a new price version of its plan applies from the renewal of
 * {@code appliesFrom} (plan D1, FR-005). The renewal at or after that date takes this price,
 * unless the price is locked. Immutable; tenant-scoped (ADR-007).
 */
@Entity
@Table(name = "price_change_notice")
class PriceChangeNotice {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "plan_price_id", nullable = false, updatable = false)
    private UUID planPriceId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "notified_at", nullable = false, updatable = false)
    private Instant notifiedAt;

    @Column(name = "applies_from", nullable = false, updatable = false)
    private Instant appliesFrom;

    protected PriceChangeNotice() {}

    PriceChangeNotice(UUID subscriptionId, UUID planPriceId, BigDecimal amount, String currency, Instant notifiedAt,
            Instant appliesFrom) {
        this.id = UUID.randomUUID();
        this.subscriptionId = subscriptionId;
        this.planPriceId = planPriceId;
        this.amount = amount;
        this.currency = currency;
        this.notifiedAt = notifiedAt;
        this.appliesFrom = appliesFrom;
    }

    UUID getPlanPriceId() {
        return planPriceId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    Instant getAppliesFrom() {
        return appliesFrom;
    }
}
