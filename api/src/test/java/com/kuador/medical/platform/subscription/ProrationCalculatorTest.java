package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Proration of an upgrade (plan D3): (new − contracted price) × remaining days ÷ period days,
 * HALF_UP to two decimals, days counted in America/Guayaquil.
 */
class ProrationCalculatorTest {

    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");
    private static final BigDecimal STANDARD = new BigDecimal("49.00");
    private static final BigDecimal PREMIUM = new BigDecimal("99.00");

    @Test
    void chargesTheDifferenceForTheRemainingDaysOfAMonth() {
        // January 2030 has 31 days; from the 16th there are 16 days left (16th to 31st).
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), noon(2030, 1, 16)))
                .isEqualByComparingTo("25.81");
    }

    @Test
    void theFirstDayChargesTheWholeDifference() {
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), day(2030, 1, 1)))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void theLastDayChargesOneDay() {
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), noon(2030, 1, 31)))
                .isEqualByComparingTo("1.61");
    }

    @Test
    void atOrAfterTheEndOfThePeriodNothingIsLeftToCharge() {
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), day(2030, 2, 1)))
                .isEqualByComparingTo("0.00");
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), noon(2030, 2, 5)))
                .as("past due: the period already ended")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void aLeapFebruaryHasTwentyNineDays() {
        assertThat(prorate(STANDARD, PREMIUM, day(2028, 2, 1), day(2028, 3, 1), noon(2028, 2, 29)))
                .as("50 × 1 ÷ 29")
                .isEqualByComparingTo("1.72");
        assertThat(prorate(STANDARD, PREMIUM, day(2028, 2, 1), day(2028, 3, 1), noon(2028, 2, 15)))
                .as("50 × 15 ÷ 29")
                .isEqualByComparingTo("25.86");
    }

    @Test
    void anAnnualPeriodOfALeapYearHas366Days() {
        // From July 1st, 2028 there are 184 days left until January 1st, 2029.
        assertThat(prorate(new BigDecimal("490.00"), new BigDecimal("1090.00"), day(2028, 1, 1), day(2029, 1, 1),
                noon(2028, 7, 1)))
                .as("600 × 184 ÷ 366")
                .isEqualByComparingTo("301.64");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        // 0.05 × 1 ÷ 2 = 0.025 → 0.03
        assertThat(prorate(new BigDecimal("10.00"), new BigDecimal("10.05"), day(2030, 1, 1), day(2030, 1, 3),
                noon(2030, 1, 2)))
                .isEqualByComparingTo("0.03");
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), noon(2030, 1, 16)).scale())
                .isEqualTo(2);
    }

    @Test
    void daysAreCountedInGuayaquilNotInUtc() {
        // 03:00 UTC on January 16th is still January 15th in Guayaquil (UTC−5): 17 days left.
        Instant lateOnThe15th = Instant.parse("2030-01-16T03:00:00Z");
        assertThat(prorate(STANDARD, PREMIUM, day(2030, 1, 1), day(2030, 2, 1), lateOnThe15th))
                .as("50 × 17 ÷ 31")
                .isEqualByComparingTo("27.42");
    }

    @Test
    void remainingDaysNeverExceedThePeriod() {
        assertThat(ProrationCalculator.remainingDays(day(2030, 1, 1), day(2030, 2, 1), noon(2029, 12, 20)))
                .isEqualTo(31);
        assertThat(ProrationCalculator.periodDays(day(2030, 1, 1), day(2030, 2, 1))).isEqualTo(31);
    }

    private static BigDecimal prorate(BigDecimal contracted, BigDecimal newPrice, Instant start, Instant end, Instant at) {
        return ProrationCalculator.prorate(contracted, newPrice, start, end, at);
    }

    private static Instant day(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(GUAYAQUIL).toInstant();
    }

    private static Instant noon(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 12, 0).atZone(GUAYAQUIL).toInstant();
    }
}
