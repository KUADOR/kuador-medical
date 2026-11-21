package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Reactivation of a CANCELLED subscription (plan D12): SUPER_ADMIN only, with reason, within retention, unpurged. */
class ReactivationPolicyTest {

    private static final Instant RETENTION_ENDS = Instant.parse("2030-08-07T05:00:00Z");

    @Test
    void withinTheRetentionAndNotPurgedItCanBeReactivated() {
        Instant now = RETENTION_ENDS.minusSeconds(1);

        assertThat(ReactivationPolicy.reactivable(SubscriptionStatus.CANCELLED, RETENTION_ENDS, false, now)).isTrue();
        ReactivationPolicy.check(RETENTION_ENDS, false, "Pago por transferencia", now);
    }

    @Test
    void theReasonIsMandatory() {
        for (String reason : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> ReactivationPolicy.check(RETENTION_ENDS, false, reason, RETENTION_ENDS.minusSeconds(60)))
                    .isInstanceOfSatisfying(ApiException.class, error -> {
                        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION);
                        assertThat(error.errors()).singleElement()
                                .satisfies(violation -> assertThat(violation.field()).isEqualTo("reason"));
                    });
        }
    }

    @Test
    void whenTheRetentionEndedTheWindowIsClosed() {
        assertThat(ReactivationPolicy.reactivable(SubscriptionStatus.CANCELLED, RETENTION_ENDS, false, RETENTION_ENDS))
                .isFalse();
        assertThatThrownBy(() -> ReactivationPolicy.check(RETENTION_ENDS, false, "Motivo", RETENTION_ENDS))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.REACTIVATION_WINDOW_CLOSED);
                    assertThat(error.status().value()).isEqualTo(409);
                    assertThat(error.getMessage()).contains("2030-08-07");
                });
    }

    @Test
    void aPurgedClinicCanNeverBeReactivated() {
        Instant now = RETENTION_ENDS.minusSeconds(3600);

        assertThat(ReactivationPolicy.reactivable(SubscriptionStatus.CANCELLED, RETENTION_ENDS, true, now)).isFalse();
        assertThatThrownBy(() -> ReactivationPolicy.check(RETENTION_ENDS, true, "Motivo", now))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.REACTIVATION_WINDOW_CLOSED));
    }

    @Test
    void onlyACancelledSubscriptionIsReactivable() {
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            if (status != SubscriptionStatus.CANCELLED) {
                assertThat(ReactivationPolicy.reactivable(status, RETENTION_ENDS, false, RETENTION_ENDS.minusSeconds(1)))
                        .isFalse();
            }
        }
        assertThat(ReactivationPolicy.reactivable(SubscriptionStatus.CANCELLED, null, false, Instant.EPOCH)).isFalse();
    }
}
