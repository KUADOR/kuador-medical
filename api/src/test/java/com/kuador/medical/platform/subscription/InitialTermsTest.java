package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Plan D4: the trial plan starts in TRIAL until the start of the day {@code trialDays} later; a
 * paid plan starts ACTIVE with a period from the start of the onboarding day. Days are counted in
 * America/Guayaquil (UTC-5, no daylight saving).
 */
class InitialTermsTest {

    @Test
    void trialEndsAtTheStartOfTheDayTrialDaysLaterInGuayaquil() {
        Instant now = Instant.parse("2030-01-01T15:00:00Z");

        assertThat(InitialTerms.trialEndsAt(now, 14)).isEqualTo(Instant.parse("2030-01-15T05:00:00Z"));
    }

    @Test
    void lateEveningInGuayaquilStillCountsAsThatLocalDay() {
        Instant now = Instant.parse("2030-01-02T03:30:00Z"); // 2030-01-01 22:30 in Guayaquil

        assertThat(InitialTerms.trialEndsAt(now, 14)).isEqualTo(Instant.parse("2030-01-15T05:00:00Z"));
        assertThat(InitialTerms.periodStart(now)).isEqualTo(Instant.parse("2030-01-01T05:00:00Z"));
    }

    @Test
    void monthlyAndAnnualPeriodsEndOnTheSameLocalDayOfTheNextMonthOrYear() {
        Instant start = InitialTerms.periodStart(Instant.parse("2030-01-31T15:00:00Z"));

        assertThat(start).isEqualTo(Instant.parse("2030-01-31T05:00:00Z"));
        assertThat(InitialTerms.periodEnd(start, BillingPeriod.MONTHLY))
                .as("end of month is clamped").isEqualTo(Instant.parse("2030-02-28T05:00:00Z"));
        assertThat(InitialTerms.periodEnd(start, BillingPeriod.ANNUAL)).isEqualTo(Instant.parse("2031-01-31T05:00:00Z"));
    }

    @Test
    void leapDayAnnualPeriodEndsOnTheLastDayOfFebruary() {
        Instant start = InitialTerms.periodStart(Instant.parse("2032-02-29T12:00:00Z"));

        assertThat(InitialTerms.periodEnd(start, BillingPeriod.ANNUAL)).isEqualTo(Instant.parse("2033-02-28T05:00:00Z"));
    }
}
