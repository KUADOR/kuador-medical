package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.api.PlanChangeKind;
import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Rules of a plan change (plan D2): its kind, when it can be requested and when it applies. */
final class PlanChangeRules {

    private PlanChangeRules() {}

    /**
     * Another billing period is a period change; in the same period, a target price higher than the
     * contracted one is an upgrade and a lower or equal one a downgrade. Without a contracted price
     * (trial) any paid target is an upgrade.
     */
    static PlanChangeKind kind(BillingPeriod current, @Nullable BigDecimal contracted, BillingPeriod target,
            BigDecimal targetPrice) {
        if (current != target) {
            return PlanChangeKind.PERIOD_CHANGE;
        }
        BigDecimal reference = contracted == null ? BigDecimal.ZERO : contracted;
        return targetPrice.compareTo(reference) > 0 ? PlanChangeKind.UPGRADE : PlanChangeKind.DOWNGRADE;
    }

    /**
     * A plan changes only in TRIAL, ACTIVE and PAST_DUE, and only to another plan or billing period.
     *
     * @throws ApiException 409 {@code PLAN_CHANGE_NOT_ALLOWED}
     */
    static void checkRequest(SubscriptionStatus status, UUID currentPlanId, BillingPeriod currentPeriod,
            UUID targetPlanId, BillingPeriod targetPeriod) {
        if (status != SubscriptionStatus.TRIAL && status != SubscriptionStatus.ACTIVE
                && status != SubscriptionStatus.PAST_DUE) {
            throw notAllowed("Plan changes are not allowed while " + status + ".");
        }
        if (currentPlanId.equals(targetPlanId) && currentPeriod == targetPeriod) {
            throw notAllowed("The target is the current plan and billing period.");
        }
    }

    /**
     * An upgrade applies at once. A downgrade or a period change waits for the renewal of an ACTIVE
     * subscription; a TRIAL has no contracted period to wait for and a PAST_DUE one already passed
     * its renewal date, so both apply at once.
     */
    static boolean appliesAtOnce(SubscriptionStatus status, PlanChangeKind kind) {
        return kind == PlanChangeKind.UPGRADE || status != SubscriptionStatus.ACTIVE;
    }

    static ApiException notAllowed(String detail) {
        return ApiException.conflict(ErrorCode.PLAN_CHANGE_NOT_ALLOWED, detail);
    }
}
