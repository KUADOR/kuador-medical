package com.kuador.medical.platform.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Inbound contract for the billing epic (plan D5, FR-019): the payment {@code paymentReference}
 * of the clinic {@code tenantId} was received. The subscription renews or becomes ACTIVE following
 * the state machine; on a CANCELLED clinic it is recorded and rejected (plan D12). Delivering the
 * same reference twice applies it once. Publish it with Spring's {@code ApplicationEventPublisher};
 * it is handled after the publisher's transaction commits.
 */
@NamedInterface("subscription")
public record PaymentReceived(UUID tenantId, String paymentReference, BigDecimal amount, String currency, Instant paidAt) {

    public PaymentReceived {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(paidAt, "paidAt");
        if (paymentReference == null || paymentReference.isBlank() || paymentReference.length() > 120) {
            throw new IllegalArgumentException("paymentReference must have 1 to 120 characters");
        }
    }
}
