package com.kuador.medical.platform.subscription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Proration of an upgrade (plan D3, ADR-015): (new − contracted price) × remaining days ÷ days of
 * the period, rounded HALF_UP to two decimals. Days are local days of America/Guayaquil; the day
 * of the upgrade counts as remaining, so an upgrade on the first day charges the whole difference
 * and one on the last day a single day.
 */
final class ProrationCalculator {

    private ProrationCalculator() {}

    static BigDecimal prorate(BigDecimal contracted, BigDecimal newPrice, Instant periodStart, Instant periodEnd, Instant at) {
        long periodDays = periodDays(periodStart, periodEnd);
        BigDecimal difference = newPrice.subtract(contracted);
        if (periodDays <= 0 || difference.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return difference.multiply(BigDecimal.valueOf(remainingDays(periodStart, periodEnd, at)))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
    }

    /** Local days of the period {@code [periodStart, periodEnd)}. */
    static long periodDays(Instant periodStart, Instant periodEnd) {
        return ChronoUnit.DAYS.between(localDay(periodStart), localDay(periodEnd));
    }

    /** Local days from the day of {@code at} to the end of the period, between 0 and the days of the period. */
    static long remainingDays(Instant periodStart, Instant periodEnd, Instant at) {
        long remaining = ChronoUnit.DAYS.between(localDay(at), localDay(periodEnd));
        return Math.clamp(remaining, 0, periodDays(periodStart, periodEnd));
    }

    private static LocalDate localDay(Instant at) {
        return LocalDate.ofInstant(at, LifecycleDates.ZONE);
    }
}
