package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Dates of a new subscription (plan D4). Days are counted in America/Guayaquil and every date is
 * the start of a local day, so a period always covers whole days.
 */
final class InitialTerms {

    static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    private InitialTerms() {}

    /** End of the trial: the start of the local day {@code trialDays} after the onboarding day. */
    static Instant trialEndsAt(Instant now, int trialDays) {
        return LocalDate.ofInstant(now, ZONE).plusDays(trialDays).atStartOfDay(ZONE).toInstant();
    }

    /** Start of the first paid period: the start of the onboarding day. */
    static Instant periodStart(Instant now) {
        return LocalDate.ofInstant(now, ZONE).atStartOfDay(ZONE).toInstant();
    }

    /** End of a period: same local day one month or one year later (clamped to the end of the month). */
    static Instant periodEnd(Instant periodStart, BillingPeriod billingPeriod) {
        LocalDate start = LocalDate.ofInstant(periodStart, ZONE);
        LocalDate end = switch (billingPeriod) {
            case MONTHLY -> start.plusMonths(1);
            case ANNUAL -> start.plusYears(1);
        };
        return end.atStartOfDay(ZONE).toInstant();
    }
}
