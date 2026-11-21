package com.kuador.medical.platform.subscription;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Inbound contract for the billing epic (plan D5): a payment of the clinic {@code tenantId}
 * failed. It is audited and never changes the status: the subscription becomes PAST_DUE only when
 * its period ends unpaid.
 */
@NamedInterface("subscription")
public record PaymentFailed(UUID tenantId, String paymentReference, Instant failedAt) {

    public PaymentFailed {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(paymentReference, "paymentReference");
        Objects.requireNonNull(failedAt, "failedAt");
    }
}
