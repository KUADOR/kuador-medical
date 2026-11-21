package com.kuador.medical.platform.plan;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The price in force at an instant for each billing period of a plan. */
final class CurrentPrices {

    /** Latest effective first; on the same instant, the latest published wins. */
    static final Comparator<PlanPrice> NEWEST_FIRST = Comparator.comparing(PlanPrice::getEffectiveFrom)
            .thenComparing(PlanPrice::getCreatedAt)
            .reversed();

    private CurrentPrices() {}

    /** One price per billing period, in period order; periods without a price in force are left out. */
    static List<PlanPrice> at(List<PlanPrice> history, Instant at) {
        Map<BillingPeriod, PlanPrice> current = new EnumMap<>(BillingPeriod.class);
        history.stream()
                .filter(price -> !price.getEffectiveFrom().isAfter(at))
                .sorted(NEWEST_FIRST)
                .forEach(price -> current.putIfAbsent(price.getBillingPeriod(), price));
        return List.copyOf(current.values());
    }
}
