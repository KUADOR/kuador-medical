package com.kuador.medical.platform.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The price in force for each billing period (ADR-015): prices are versions, never edited. */
class CurrentPricesTest {

    private static final UUID PLAN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2030-02-15T15:00:00Z");

    @Test
    void latestEffectivePriceOfEachPeriodIsInForce() {
        PlanPrice oldMonthly = price(BillingPeriod.MONTHLY, "49.00", "2030-01-01T05:00:00Z", "2029-12-20T15:00:00Z");
        PlanPrice newMonthly = price(BillingPeriod.MONTHLY, "55.00", "2030-02-01T05:00:00Z", "2030-01-15T15:00:00Z");
        PlanPrice annual = price(BillingPeriod.ANNUAL, "490.00", "2030-01-01T05:00:00Z", "2029-12-20T15:00:00Z");

        assertThat(CurrentPrices.at(List.of(oldMonthly, annual, newMonthly), NOW))
                .containsExactly(newMonthly, annual);
    }

    @Test
    void futurePriceIsNotInForceYet() {
        PlanPrice current = price(BillingPeriod.MONTHLY, "49.00", "2030-01-01T05:00:00Z", "2029-12-20T15:00:00Z");
        PlanPrice future = price(BillingPeriod.MONTHLY, "55.00", "2030-03-01T05:00:00Z", "2030-01-15T15:00:00Z");

        assertThat(CurrentPrices.at(List.of(future, current), NOW)).containsExactly(current);
    }

    @Test
    void onTheSameEffectiveInstantTheLatestPublishedWins() {
        PlanPrice first = price(BillingPeriod.MONTHLY, "49.00", "2030-02-01T05:00:00Z", "2030-01-10T15:00:00Z");
        PlanPrice correction = price(BillingPeriod.MONTHLY, "45.00", "2030-02-01T05:00:00Z", "2030-01-12T15:00:00Z");

        assertThat(CurrentPrices.at(List.of(first, correction), NOW)).containsExactly(correction);
    }

    @Test
    void planWithoutPricesHasNoCurrentPrices() {
        assertThat(CurrentPrices.at(List.of(), NOW)).isEmpty();
    }

    @Test
    void amountsKeepTwoDecimals() {
        assertThat(price(BillingPeriod.MONTHLY, "49.00", "2030-01-01T05:00:00Z", "2029-12-20T15:00:00Z")
                .getAmount().toPlainString()).isEqualTo("49.00");
    }

    private static PlanPrice price(BillingPeriod period, String amount, String effectiveFrom, String createdAt) {
        return new PlanPrice(UUID.randomUUID(), PLAN, period, "USD", new BigDecimal(amount),
                Instant.parse(effectiveFrom), null, Instant.parse(createdAt));
    }
}
