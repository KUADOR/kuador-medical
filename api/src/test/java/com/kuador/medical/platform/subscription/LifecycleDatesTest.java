package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Day counts in America/Guayaquil (plan § Contexto técnico) and the renewal of a price change (D1). */
class LifecycleDatesTest {

    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");

    private static Instant day(String date) {
        return LocalDate.parse(date).atStartOfDay(GUAYAQUIL).toInstant();
    }

    @Test
    void daysAfterCountsLocalDaysFromTheStartOfTheDay() {
        // 23:30 in Guayaquil on 2030-01-31 is already 2030-02-01 in UTC.
        Instant lateEvening = Instant.parse("2030-02-01T04:30:00Z");

        assertThat(LifecycleDates.daysAfter(lateEvening, 7)).isEqualTo(day("2030-02-07"));
        assertThat(LifecycleDates.daysAfter(lateEvening, 0)).isEqualTo(day("2030-01-31"));
    }

    @Test
    void graceSuspensionAndRetentionDeadlines() {
        Instant periodEnd = day("2030-02-01");

        assertThat(LifecycleDates.daysAfter(periodEnd, 7)).isEqualTo(day("2030-02-08"));
        assertThat(LifecycleDates.daysAfter(day("2030-02-08"), 90)).isEqualTo(day("2030-05-09"));
        assertThat(LifecycleDates.daysAfter(day("2030-05-09"), 90)).isEqualTo(day("2030-08-07"));
    }

    @Test
    void renewalsKeepTheAnchorDayAcrossShortMonthsAndLeapYears() {
        Instant periodEnd = day("2031-01-31");

        assertThat(LifecycleDates.renewal(periodEnd, BillingPeriod.MONTHLY, 1)).isEqualTo(day("2031-02-28"));
        assertThat(LifecycleDates.renewal(periodEnd, BillingPeriod.MONTHLY, 2)).isEqualTo(day("2031-03-31"));
        assertThat(LifecycleDates.renewal(day("2032-02-29"), BillingPeriod.ANNUAL, 1)).isEqualTo(day("2033-02-28"));
    }

    @Test
    void aPriceChangeAppliesAtTheFirstRenewalAtLeastTheNoticeDaysAfterTheNotice() {
        Instant periodEnd = day("2030-02-01");
        Instant notifiedAt = Instant.parse("2030-01-10T15:00:00Z");

        // 30 days after 2030-01-10 is 2030-02-09: the renewal of 2030-02-01 is too early.
        assertThat(LifecycleDates.priceChangeAppliesFrom(periodEnd, BillingPeriod.MONTHLY, notifiedAt, 30,
                notifiedAt, null)).isEqualTo(day("2030-03-01"));
        // Notified 31 days before the renewal: it applies there.
        assertThat(LifecycleDates.priceChangeAppliesFrom(periodEnd, BillingPeriod.MONTHLY,
                Instant.parse("2030-01-01T15:00:00Z"), 30, Instant.parse("2030-01-01T15:00:00Z"), null))
                .isEqualTo(day("2030-02-01"));
    }

    @Test
    void aPriceThatStartsLaterOrALockedPricePostponesTheChange() {
        Instant periodEnd = day("2030-02-01");
        Instant notifiedAt = Instant.parse("2029-12-01T15:00:00Z");

        assertThat(LifecycleDates.priceChangeAppliesFrom(periodEnd, BillingPeriod.MONTHLY, notifiedAt, 30,
                day("2030-03-15"), null)).isEqualTo(day("2030-04-01"));
        assertThat(LifecycleDates.priceChangeAppliesFrom(periodEnd, BillingPeriod.MONTHLY, notifiedAt, 30,
                notifiedAt, LocalDate.parse("2030-05-01"))).isEqualTo(day("2030-06-01"));
        assertThat(LifecycleDates.priceChangeAppliesFrom(periodEnd, BillingPeriod.ANNUAL, notifiedAt, 30,
                notifiedAt, LocalDate.parse("2030-05-01"))).isEqualTo(day("2031-02-01"));
    }

    @Test
    void aLockCoversThePeriodsThatStartUpToItsLastDay() {
        LocalDate lockedUntil = LocalDate.parse("2030-03-01");

        assertThat(LifecycleDates.lockCovers(lockedUntil, day("2030-03-01"))).isTrue();
        assertThat(LifecycleDates.lockCovers(lockedUntil, day("2030-03-02"))).isFalse();
        assertThat(LifecycleDates.lockCovers(null, day("2030-01-01"))).isFalse();
        assertThat(LifecycleDates.lockedUntil(LifecycleDates.lockInstant(lockedUntil))).isEqualTo(lockedUntil);
    }
}
