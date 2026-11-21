package com.kuador.medical.platform.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Request bodies of {@code /platform/plans} ({@code platform.openapi.yaml}). */
final class PlanRequests {

    static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,39}$";
    static final String MONEY_PATTERN = "^\\d{1,10}\\.\\d{2}$";
    static final String CURRENCY_PATTERN = "^[A-Z]{3}$";

    private PlanRequests() {}

    /** {@code CreatePlanRequest}: modules and limits arrive as codes so unknown ones get a field error. */
    record CreatePlan(
            @Nullable UUID id,
            @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
            @NotBlank @Size(max = 80) String name,
            @Nullable @Size(max = 500) String description,
            @NotNull Boolean trial,
            @NotNull List<@Nullable String> modules,
            @NotNull Map<String, @Nullable Integer> limits,
            @Nullable List<@Valid @NotNull PriceInput> prices) {}

    /** {@code PriceInput}: an initial price of a new plan. */
    record PriceInput(
            @NotNull BillingPeriod billingPeriod,
            @Nullable @Pattern(regexp = CURRENCY_PATTERN) String currency,
            @NotNull @Pattern(regexp = MONEY_PATTERN) String amount) {}

    /** {@code UpdatePlanRequest}. */
    record UpdatePlan(
            @NotBlank @Size(max = 80) String name,
            @Nullable @Size(max = 500) String description,
            @NotNull List<@Nullable String> modules,
            @NotNull Map<String, @Nullable Integer> limits,
            @NotNull @PositiveOrZero Long version) {}

    /** {@code CreatePlanPriceRequest}. */
    record CreatePrice(
            @NotNull BillingPeriod billingPeriod,
            @Nullable @Pattern(regexp = CURRENCY_PATTERN) String currency,
            @NotNull @Pattern(regexp = MONEY_PATTERN) String amount,
            @NotNull Instant effectiveFrom,
            @Nullable UUID id) {}
}
