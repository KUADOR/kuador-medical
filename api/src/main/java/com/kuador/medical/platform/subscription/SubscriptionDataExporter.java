package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.api.TenantDataExporter;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sections {@code platform.subscription}, {@code platform.subscription.periods} and
 * {@code platform.subscription.transitions} of the data export: the contracted prices and the
 * status history of the clinic (ADR-015, ADR-016). The name of the SUPER_ADMIN is left out.
 */
class SubscriptionDataExporter {

    private SubscriptionDataExporter() {}

    private static String planName(PlanCatalog plans, UUID planId) {
        return plans.find(planId).map(OfferedPlan::name).orElse("");
    }

    @Component
    static class Current implements TenantDataExporter {

        private final SubscriptionRepository subscriptions;
        private final PlanCatalog plans;

        Current(SubscriptionRepository subscriptions, PlanCatalog plans) {
            this.subscriptions = subscriptions;
            this.plans = plans;
        }

        @Override
        public String section() {
            return "platform.subscription";
        }

        @Override
        public List<Map<String, Object>> rows() {
            return subscriptions.findCurrent().map(s -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", s.getId());
                row.put("plan", planName(plans, s.getPlanId()));
                row.put("billingPeriod", s.getBillingPeriod());
                row.put("status", s.getStatus());
                row.put("priceAmount", s.getPriceAmountSnapshot());
                row.put("currency", s.getPriceCurrencySnapshot());
                row.put("currentPeriodStart", s.getCurrentPeriodStart());
                row.put("currentPeriodEnd", s.getCurrentPeriodEnd());
                row.put("trialEndsAt", s.getTrialEndsAt());
                row.put("graceEndsAt", s.getGraceEndsAt());
                row.put("suspendedAt", s.getSuspendedAt());
                row.put("cancelledAt", s.getCancelledAt());
                row.put("retentionEndsAt", s.getRetentionEndsAt());
                return List.<Map<String, Object>>of(row);
            }).orElse(List.of());
        }
    }

    @Component
    static class Periods implements TenantDataExporter {

        private final SubscriptionRepository subscriptions;
        private final SubscriptionHistoryRepository history;
        private final PlanCatalog plans;

        Periods(SubscriptionRepository subscriptions, SubscriptionHistoryRepository history, PlanCatalog plans) {
            this.subscriptions = subscriptions;
            this.history = history;
            this.plans = plans;
        }

        @Override
        public String section() {
            return "platform.subscription.periods";
        }

        @Override
        public List<Map<String, Object>> rows() {
            Optional<Subscription> current = subscriptions.findCurrent();
            if (current.isEmpty()) {
                return List.of();
            }
            return history.periods(current.get().getId()).stream().map(period -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("plan", planName(plans, period.getPlanId()));
                row.put("periodStart", period.getPeriodStart());
                row.put("periodEnd", period.getPeriodEnd());
                row.put("amount", period.getAmount());
                row.put("currency", period.getCurrency());
                return row;
            }).toList();
        }
    }

    @Component
    static class Transitions implements TenantDataExporter {

        private final SubscriptionRepository subscriptions;
        private final SubscriptionHistoryRepository history;

        Transitions(SubscriptionRepository subscriptions, SubscriptionHistoryRepository history) {
            this.subscriptions = subscriptions;
            this.history = history;
        }

        @Override
        public String section() {
            return "platform.subscription.transitions";
        }

        @Override
        public List<Map<String, Object>> rows() {
            Optional<Subscription> current = subscriptions.findCurrent();
            if (current.isEmpty()) {
                return List.of();
            }
            return history.transitions(current.get().getId()).stream().map(transition -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("from", transition.getFromStatus());
                row.put("to", transition.getToStatus());
                row.put("cause", transition.getCause());
                row.put("at", transition.getAt());
                return row;
            }).toList();
        }
    }
}
