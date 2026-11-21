package com.kuador.medical.platform.offboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.platform.subscription.SubscriptionStatus;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

/** When a clinic can be purged (MEDK-49 CA 15, FR-021, ADR-018). */
class PurgeEligibilityTest {

    private static final Instant NOW = Instant.parse("2030-08-07T05:00:00Z");

    @Test
    void aCancelledClinicPastItsRetentionCanBePurged() {
        assertThat(PurgeEligibility.eligible(SubscriptionStatus.CANCELLED, NOW, false, NOW)).isTrue();
        assertThat(PurgeEligibility.eligible(SubscriptionStatus.CANCELLED, NOW.minus(Duration.ofDays(1)), false, NOW)).isTrue();
        assertThatCode(() -> PurgeEligibility.check(SubscriptionStatus.CANCELLED, NOW, false, NOW)).doesNotThrowAnyException();
    }

    @Test
    void aPurgeBeforeTheEndOfTheRetentionIsRejected() {
        Instant retentionEnd = NOW.plusSeconds(1);

        assertThat(PurgeEligibility.eligible(SubscriptionStatus.CANCELLED, retentionEnd, false, NOW)).isFalse();
        assertThatThrownBy(() -> PurgeEligibility.check(SubscriptionStatus.CANCELLED, retentionEnd, false, NOW))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.PURGE_NOT_ALLOWED);
                    assertThat(e.getMessage()).contains("2030-08-07");
                });
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
    void onlyACancelledClinicCanBePurged(SubscriptionStatus status) {
        assertThat(PurgeEligibility.eligible(status, NOW.minus(Duration.ofDays(1)), false, NOW)).isFalse();
        assertThatThrownBy(() -> PurgeEligibility.check(status, NOW.minus(Duration.ofDays(1)), false, NOW))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PURGE_NOT_ALLOWED));
    }

    @Test
    void aPurgedClinicOrOneWithoutRetentionIsRejected() {
        assertThatThrownBy(() -> PurgeEligibility.check(SubscriptionStatus.CANCELLED, NOW, true, NOW))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PURGE_NOT_ALLOWED));
        assertThatThrownBy(() -> PurgeEligibility.check(SubscriptionStatus.CANCELLED, null, false, NOW))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PURGE_NOT_ALLOWED));
    }
}
