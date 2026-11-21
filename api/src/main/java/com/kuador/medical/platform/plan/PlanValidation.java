package com.kuador.medical.platform.plan;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Rules of plan requests that Bean Validation cannot express, reported as {@code VALIDATION}
 * field errors with the codes of the contract ({@code UNKNOWN_MODULE}, {@code UNKNOWN_LIMIT},
 * {@code DUPLICATE}, {@code IN_THE_PAST}…).
 */
final class PlanValidation {

    /** Only USD is offered today (Ecuador); the model keeps the currency for other countries. */
    static final String SUPPORTED_CURRENCY = "USD";

    /** Clock skew accepted on {@code effectiveFrom} before it counts as in the past. */
    static final java.time.Duration CLOCK_SKEW = java.time.Duration.ofMinutes(1);

    private final List<FieldViolation> errors = new ArrayList<>();

    /** Modules of the catalog; unknown and repeated codes are field errors. */
    Set<ModuleCode> modules(List<@Nullable String> codes) {
        Set<ModuleCode> modules = EnumSet.noneOf(ModuleCode.class);
        for (int i = 0; i < codes.size(); i++) {
            String field = "modules[" + i + "]";
            Optional<ModuleCode> module = parse(ModuleCode.class, codes.get(i));
            if (module.isEmpty()) {
                errors.add(new FieldViolation(field, "UNKNOWN_MODULE"));
            } else if (!modules.add(module.get())) {
                errors.add(new FieldViolation(field, "DUPLICATE"));
            }
        }
        return modules;
    }

    /** Every limit of the catalog; a missing or {@code null} value means unlimited. */
    Map<LimitCode, @Nullable Integer> limits(Map<String, @Nullable Integer> values) {
        Map<LimitCode, @Nullable Integer> limits = new EnumMap<>(LimitCode.class);
        for (LimitCode code : LimitCode.values()) {
            limits.put(code, null);
        }
        values.forEach((key, value) -> {
            Optional<LimitCode> code = parse(LimitCode.class, key);
            if (code.isEmpty()) {
                errors.add(new FieldViolation("limits." + key, "UNKNOWN_LIMIT"));
            } else if (value != null && value < 0) {
                errors.add(new FieldViolation("limits." + key, "INVALID"));
            } else {
                limits.put(code.get(), value);
            }
        });
        return limits;
    }

    /** Currency of a price, {@code USD} when omitted. */
    String currency(String field, @Nullable String currency) {
        if (currency == null || SUPPORTED_CURRENCY.equals(currency)) {
            return SUPPORTED_CURRENCY;
        }
        errors.add(new FieldViolation(field, "UNSUPPORTED_CURRENCY"));
        return currency;
    }

    BigDecimal amount(String amount) {
        return new BigDecimal(amount);
    }

    void notInThePast(String field, Instant value, Instant now) {
        if (value.isBefore(now.minus(CLOCK_SKEW))) {
            errors.add(new FieldViolation(field, "IN_THE_PAST"));
        }
    }

    void add(String field, String code) {
        errors.add(new FieldViolation(field, code));
    }

    /** Throws 400 {@code VALIDATION} with every error found so far. */
    void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    static ApiException trialPlanRequired() {
        return ApiException.validation(List.of(new FieldViolation("trial", "TRIAL_PLAN_REQUIRED")));
    }

    private static <E extends Enum<E>> Optional<E> parse(Class<E> type, @Nullable String code) {
        return Arrays.stream(type.getEnumConstants()).filter(value -> value.name().equals(code)).findFirst();
    }
}
