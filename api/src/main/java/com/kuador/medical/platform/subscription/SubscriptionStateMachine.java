package com.kuador.medical.platform.subscription;

import static com.kuador.medical.platform.subscription.SubscriptionStatus.ACTIVE;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.CANCELLED;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.PAST_DUE;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.SUSPENDED;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.TRIAL;
import static com.kuador.medical.platform.subscription.TransitionCause.MANUAL;
import static com.kuador.medical.platform.subscription.TransitionCause.PAYMENT_RECEIVED;
import static com.kuador.medical.platform.subscription.TransitionCause.SCHEDULER;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.List;
import java.util.Set;

/**
 * Transition table of the subscription (MEDK-49 plan § Máquina de estados, ADR-016). Any
 * transition not listed is rejected with {@code INVALID_SUBSCRIPTION_TRANSITION}; in particular a
 * trial that ends unpaid is suspended (never cancelled), a {@code PaymentReceived} never reactivates
 * a cancelled subscription and a {@code PaymentFailed} never changes the status (plan D5). Leaving
 * {@code CANCELLED} also needs {@link ReactivationPolicy}.
 */
final class SubscriptionStateMachine {

    private record Transition(SubscriptionStatus from, SubscriptionStatus to, TransitionCause cause) {}

    private static final Set<Transition> TABLE = Set.of(
            new Transition(TRIAL, ACTIVE, PAYMENT_RECEIVED),
            new Transition(TRIAL, ACTIVE, MANUAL),
            new Transition(TRIAL, SUSPENDED, SCHEDULER),
            new Transition(ACTIVE, ACTIVE, PAYMENT_RECEIVED),
            new Transition(ACTIVE, PAST_DUE, SCHEDULER),
            new Transition(PAST_DUE, ACTIVE, PAYMENT_RECEIVED),
            new Transition(PAST_DUE, ACTIVE, MANUAL),
            new Transition(PAST_DUE, SUSPENDED, SCHEDULER),
            new Transition(PAST_DUE, SUSPENDED, MANUAL),
            new Transition(ACTIVE, SUSPENDED, MANUAL),
            new Transition(SUSPENDED, ACTIVE, PAYMENT_RECEIVED),
            new Transition(SUSPENDED, ACTIVE, MANUAL),
            new Transition(SUSPENDED, CANCELLED, SCHEDULER),
            new Transition(SUSPENDED, CANCELLED, MANUAL),
            new Transition(TRIAL, CANCELLED, MANUAL),
            new Transition(ACTIVE, CANCELLED, MANUAL),
            new Transition(PAST_DUE, CANCELLED, MANUAL),
            new Transition(CANCELLED, ACTIVE, MANUAL));

    private SubscriptionStateMachine() {}

    static boolean allows(SubscriptionStatus from, SubscriptionStatus to, TransitionCause cause) {
        return TABLE.contains(new Transition(from, to, cause));
    }

    /** @throws ApiException 409 {@code INVALID_SUBSCRIPTION_TRANSITION} when the table does not list it */
    static void check(SubscriptionStatus from, SubscriptionStatus to, TransitionCause cause) {
        if (!allows(from, to, cause)) {
            throw invalid(from, to);
        }
    }

    /** Statuses a SUPER_ADMIN can move {@code from} to, in status order (before the reactivation window check). */
    static List<SubscriptionStatus> manualTargets(SubscriptionStatus from) {
        return java.util.Arrays.stream(SubscriptionStatus.values())
                .filter(to -> to != from && allows(from, to, MANUAL))
                .toList();
    }

    static ApiException invalid(SubscriptionStatus from, SubscriptionStatus to) {
        return ApiException.conflict(ErrorCode.INVALID_SUBSCRIPTION_TRANSITION,
                "Transition from " + from + " to " + to + " is not allowed.");
    }
}
