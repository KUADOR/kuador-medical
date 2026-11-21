package com.kuador.medical.platform.subscription;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

/**
 * Published inside the transaction of every status change of a subscription (ADR-016), renewals
 * excluded. A reactivation from CANCELLED has {@code from = CANCELLED}: the purge (MEDK-70)
 * listens to it with a plain {@code @EventListener} to cancel a pending purge request in the same
 * transaction (plan D12).
 */
@NamedInterface("subscription")
public record SubscriptionStatusChanged(
        UUID tenantId,
        UUID subscriptionId,
        @Nullable SubscriptionStatus from,
        SubscriptionStatus to,
        TransitionCause cause,
        Instant at) {}
