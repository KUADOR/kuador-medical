package com.kuador.medical.platform.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response bodies of {@code /platform/plans} and {@code /platform/catalog} ({@code platform.openapi.yaml}). */
final class PlanResponses {

    private PlanResponses() {}

    /** {@code Plan}: every limit of the catalog is present, {@code null} meaning unlimited. */
    record PlanResponse(
            UUID id,
            String code,
            String name,
            @Nullable String description,
            boolean trial,
            PlanStatus status,
            List<ModuleCode> modules,
            Map<LimitCode, @Nullable Integer> limits,
            List<PriceResponse> currentPrices,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static PlanResponse of(Plan plan, List<PlanPrice> currentPrices) {
            return new PlanResponse(
                    plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.isTrial(),
                    plan.getStatus(), List.copyOf(plan.getModules()), plan.getLimits(),
                    currentPrices.stream().map(PriceResponse::of).toList(),
                    plan.getCreatedAt(), plan.getUpdatedAt(), plan.getVersion());
        }
    }

    /** {@code PlanPrice}: amount as a decimal string with two decimals, without VAT. */
    record PriceResponse(
            UUID id, BillingPeriod billingPeriod, String currency, String amount, Instant effectiveFrom, Instant createdAt) {

        static PriceResponse of(PlanPrice price) {
            return new PriceResponse(
                    price.getId(), price.getBillingPeriod(), price.getCurrency(), price.getAmount().toPlainString(),
                    price.getEffectiveFrom(), price.getCreatedAt());
        }
    }

    /** {@code ModuleInfo}. */
    record ModuleInfo(ModuleCode code, String label) {}

    /** {@code LimitInfo}. */
    record LimitInfo(LimitCode code, String label, LimitCode.Unit unit) {}
}
