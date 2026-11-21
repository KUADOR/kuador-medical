package com.kuador.medical.platform.subscription;

import static com.kuador.medical.platform.subscription.SubscriptionStatus.ACTIVE;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.CANCELLED;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.PAST_DUE;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.SUSPENDED;
import static com.kuador.medical.platform.subscription.SubscriptionStatus.TRIAL;
import static com.kuador.medical.platform.subscription.TransitionCause.MANUAL;
import static com.kuador.medical.platform.subscription.TransitionCause.PAYMENT_FAILED;
import static com.kuador.medical.platform.subscription.TransitionCause.PAYMENT_RECEIVED;
import static com.kuador.medical.platform.subscription.TransitionCause.SCHEDULER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The transition table of the subscription (MEDK-49 plan § Máquina de estados, ADR-016). */
class SubscriptionStateMachineTest {

    static List<Arguments> allowedTransitions() {
        return List.of(
                Arguments.of(TRIAL, ACTIVE, PAYMENT_RECEIVED),
                Arguments.of(TRIAL, ACTIVE, MANUAL),
                Arguments.of(TRIAL, SUSPENDED, SCHEDULER),
                Arguments.of(ACTIVE, ACTIVE, PAYMENT_RECEIVED),
                Arguments.of(ACTIVE, PAST_DUE, SCHEDULER),
                Arguments.of(PAST_DUE, ACTIVE, PAYMENT_RECEIVED),
                Arguments.of(PAST_DUE, ACTIVE, MANUAL),
                Arguments.of(PAST_DUE, SUSPENDED, SCHEDULER),
                Arguments.of(PAST_DUE, SUSPENDED, MANUAL),
                Arguments.of(ACTIVE, SUSPENDED, MANUAL),
                Arguments.of(SUSPENDED, ACTIVE, PAYMENT_RECEIVED),
                Arguments.of(SUSPENDED, ACTIVE, MANUAL),
                Arguments.of(SUSPENDED, CANCELLED, SCHEDULER),
                Arguments.of(SUSPENDED, CANCELLED, MANUAL),
                Arguments.of(TRIAL, CANCELLED, MANUAL),
                Arguments.of(ACTIVE, CANCELLED, MANUAL),
                Arguments.of(PAST_DUE, CANCELLED, MANUAL),
                Arguments.of(CANCELLED, ACTIVE, MANUAL));
    }

    @ParameterizedTest(name = "{0} -> {1} by {2}")
    @MethodSource("allowedTransitions")
    void everyTransitionOfTheTableIsAllowed(SubscriptionStatus from, SubscriptionStatus to, TransitionCause cause) {
        assertThat(SubscriptionStateMachine.allows(from, to, cause)).isTrue();
        SubscriptionStateMachine.check(from, to, cause);
    }

    @Test
    void everyTransitionOutsideTheTableIsRejected() {
        Set<String> allowed = new java.util.HashSet<>();
        for (Arguments arguments : allowedTransitions()) {
            Object[] values = arguments.get();
            allowed.add(values[0] + ">" + values[1] + ">" + values[2]);
        }
        int rejected = 0;
        for (SubscriptionStatus from : SubscriptionStatus.values()) {
            for (SubscriptionStatus to : SubscriptionStatus.values()) {
                for (TransitionCause cause : TransitionCause.values()) {
                    if (allowed.contains(from + ">" + to + ">" + cause)) {
                        continue;
                    }
                    rejected++;
                    assertThat(SubscriptionStateMachine.allows(from, to, cause)).as("%s -> %s by %s", from, to, cause)
                            .isFalse();
                    assertThatThrownBy(() -> SubscriptionStateMachine.check(from, to, cause))
                            .isInstanceOfSatisfying(ApiException.class, error -> {
                                assertThat(error.code()).isEqualTo(ErrorCode.INVALID_SUBSCRIPTION_TRANSITION);
                                assertThat(error.status().value()).isEqualTo(409);
                            });
                }
            }
        }
        assertThat(rejected).isPositive();
    }

    @Test
    void aTrialThatEndsWithoutPaymentIsSuspendedNeverCancelled() {
        assertThat(SubscriptionStateMachine.allows(TRIAL, CANCELLED, SCHEDULER)).isFalse();
        assertThat(SubscriptionStateMachine.allows(TRIAL, SUSPENDED, SCHEDULER)).isTrue();
    }

    @Test
    void aPaymentNeverReactivatesACancelledSubscription() {
        assertThat(SubscriptionStateMachine.allows(CANCELLED, ACTIVE, PAYMENT_RECEIVED)).isFalse();
    }

    @Test
    void aFailedPaymentNeverChangesTheStatus() {
        for (SubscriptionStatus from : SubscriptionStatus.values()) {
            for (SubscriptionStatus to : SubscriptionStatus.values()) {
                assertThat(SubscriptionStateMachine.allows(from, to, PAYMENT_FAILED)).isFalse();
            }
        }
    }

    @Test
    void manualTargetsFollowTheTable() {
        assertThat(SubscriptionStateMachine.manualTargets(TRIAL)).containsExactly(ACTIVE, CANCELLED);
        assertThat(SubscriptionStateMachine.manualTargets(ACTIVE)).containsExactly(SUSPENDED, CANCELLED);
        assertThat(SubscriptionStateMachine.manualTargets(PAST_DUE)).containsExactly(ACTIVE, SUSPENDED, CANCELLED);
        assertThat(SubscriptionStateMachine.manualTargets(SUSPENDED)).containsExactly(ACTIVE, CANCELLED);
        assertThat(SubscriptionStateMachine.manualTargets(CANCELLED)).containsExactly(ACTIVE);
        assertThat(EnumSet.copyOf(SubscriptionStateMachine.manualTargets(ACTIVE))).doesNotContain(ACTIVE);
    }
}
