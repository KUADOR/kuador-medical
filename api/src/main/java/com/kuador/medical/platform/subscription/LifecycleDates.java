package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/**
 * Dates of the subscription lifecycle. Days are counted in America/Guayaquil and every deadline
 * is the start of a local day (like {@link InitialTerms}), so a deadline computed twice from the
 * same data is always the same instant: the lifecycle job is idempotent.
 */
final class LifecycleDates {

    static final ZoneId ZONE = InitialTerms.ZONE;

    private LifecycleDates() {}

    /** The start of the local day {@code days} after the local day of {@code at}. */
    static Instant daysAfter(Instant at, int days) {
        return LocalDate.ofInstant(at, ZONE).plusDays(days).atStartOfDay(ZONE).toInstant();
    }

    /** The start of the local day of {@code at}. */
    static Instant startOfDay(Instant at) {
        return daysAfter(at, 0);
    }

    /**
     * The {@code n}-th renewal after {@code periodEnd}, counted from the same anchor day, so that a
     * period that ends on the 31st renews on the last day of shorter months and back on the 31st.
     */
    static Instant renewal(Instant periodEnd, BillingPeriod billingPeriod, int n) {
        LocalDate anchor = LocalDate.ofInstant(periodEnd, ZONE);
        LocalDate date = switch (billingPeriod) {
            case MONTHLY -> anchor.plusMonths(n);
            case ANNUAL -> anchor.plusYears(n);
        };
        return date.atStartOfDay(ZONE).toInstant();
    }

    /**
     * First renewal (a period start, {@code periodEnd} being the next one) when a new price applies
     * (plan D1, ADR-015): at least {@code noticeDays} after the notice, not before the price is in
     * force and after the last day of a price lock.
     */
    static Instant priceChangeAppliesFrom(Instant periodEnd, BillingPeriod billingPeriod, Instant notifiedAt,
            int noticeDays, Instant priceEffectiveFrom, @Nullable LocalDate lockedUntil) {
        Instant earliest = latest(daysAfter(notifiedAt, noticeDays), priceEffectiveFrom);
        if (lockedUntil != null) {
            earliest = latest(earliest, lockedUntil.plusDays(1).atStartOfDay(ZONE).toInstant());
        }
        for (int n = 0; ; n++) {
            Instant renewal = renewal(periodEnd, billingPeriod, n);
            if (!renewal.isBefore(earliest)) {
                return renewal;
            }
        }
    }

    /** Whether a price lock whose last day is {@code lockedUntil} keeps the price of a period starting at {@code periodStart}. */
    static boolean lockCovers(@Nullable LocalDate lockedUntil, Instant periodStart) {
        return lockedUntil != null && !LocalDate.ofInstant(periodStart, ZONE).isAfter(lockedUntil);
    }

    /** Stored form of the last day of a price lock: the start of that local day. */
    static Instant lockInstant(LocalDate lockedUntil) {
        return lockedUntil.atStartOfDay(ZONE).toInstant();
    }

    static LocalDate lockedUntil(Instant stored) {
        return LocalDate.ofInstant(stored, ZONE);
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
