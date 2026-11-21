package com.kuador.medical.platform.plan;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the rest of the platform needs to know about a plan to sell it (MEDK-66): its name,
 * whether it is the trial plan or archived, and its prices in force. Plans themselves stay private
 * to this package.
 */
@Service
public class PlanCatalog {

    private final PlanRepository plans;
    private final PlanPriceRepository prices;
    private final Clock clock = Clock.systemUTC();

    PlanCatalog(PlanRepository plans, PlanPriceRepository prices) {
        this.plans = plans;
        this.prices = prices;
    }

    /** The plan with its prices in force now, if it exists. */
    @Transactional(readOnly = true)
    public Optional<OfferedPlan> find(UUID planId) {
        return plans.findById(planId).map(plan -> new OfferedPlan(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.isTrial(),
                plan.getStatus() == PlanStatus.ARCHIVED,
                CurrentPrices.at(prices.findHistory(planId), clock.instant()).stream()
                        .map(price -> new CurrentPrice(
                                price.getId(), price.getBillingPeriod(), price.getCurrency(), price.getAmount()))
                        .toList()));
    }

    /**
     * The newest price version of a billing period of the plan, even if it is not in force yet
     * (plan D1: the OWNER is notified of a future price as soon as it is published).
     */
    @Transactional(readOnly = true)
    public Optional<PriceVersion> latestPrice(UUID planId, BillingPeriod billingPeriod) {
        return prices.findHistory(planId).stream()
                .filter(price -> price.getBillingPeriod() == billingPeriod)
                .sorted(CurrentPrices.NEWEST_FIRST)
                .findFirst()
                .map(price -> new PriceVersion(price.getId(), price.getCurrency(), price.getAmount(), price.getEffectiveFrom()));
    }

    /** A price version of a plan, without VAT. */
    public record PriceVersion(UUID priceId, String currency, BigDecimal amount, java.time.Instant effectiveFrom) {}

    /** A plan as it is offered now. */
    public record OfferedPlan(
            UUID id, String code, String name, boolean trial, boolean archived, List<CurrentPrice> currentPrices) {

        public Optional<CurrentPrice> priceFor(BillingPeriod billingPeriod) {
            return currentPrices.stream().filter(price -> price.billingPeriod() == billingPeriod).findFirst();
        }
    }

    /** The price in force of a billing period, without VAT. */
    public record CurrentPrice(UUID priceId, BillingPeriod billingPeriod, String currency, BigDecimal amount) {}
}
