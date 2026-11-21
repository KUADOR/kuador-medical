package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.platform.api.PlanChangeKind;
import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Kind of a plan change (plan D2) and when it can be requested. */
class PlanChangeRulesTest {

    private static final UUID CURRENT = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Test
    void aHigherPriceInTheSameBillingPeriodIsAnUpgrade() {
        assertThat(kind(BillingPeriod.MONTHLY, "49.00", BillingPeriod.MONTHLY, "99.00")).isEqualTo(PlanChangeKind.UPGRADE);
    }

    @Test
    void aLowerOrEqualPriceIsADowngrade() {
        assertThat(kind(BillingPeriod.MONTHLY, "49.00", BillingPeriod.MONTHLY, "29.00")).isEqualTo(PlanChangeKind.DOWNGRADE);
        assertThat(kind(BillingPeriod.MONTHLY, "49.00", BillingPeriod.MONTHLY, "49.00")).isEqualTo(PlanChangeKind.DOWNGRADE);
    }

    @Test
    void anotherBillingPeriodIsAPeriodChangeWhateverThePrice() {
        assertThat(kind(BillingPeriod.MONTHLY, "49.00", BillingPeriod.ANNUAL, "990.00"))
                .isEqualTo(PlanChangeKind.PERIOD_CHANGE);
        assertThat(kind(BillingPeriod.ANNUAL, "490.00", BillingPeriod.MONTHLY, "9.00"))
                .isEqualTo(PlanChangeKind.PERIOD_CHANGE);
    }

    @Test
    void withoutAContractedPriceAnyPaidTargetIsAnUpgrade() {
        assertThat(PlanChangeRules.kind(BillingPeriod.MONTHLY, null, BillingPeriod.MONTHLY, new BigDecimal("29.00")))
                .isEqualTo(PlanChangeKind.UPGRADE);
    }

    @Test
    void onlyTrialActiveAndPastDueSubscriptionsChangePlan() {
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            boolean allowed = status == SubscriptionStatus.TRIAL || status == SubscriptionStatus.ACTIVE
                    || status == SubscriptionStatus.PAST_DUE;
            if (allowed) {
                PlanChangeRules.checkRequest(status, CURRENT, BillingPeriod.MONTHLY, TARGET, BillingPeriod.MONTHLY);
            } else {
                assertThatThrownBy(() -> PlanChangeRules.checkRequest(
                        status, CURRENT, BillingPeriod.MONTHLY, TARGET, BillingPeriod.MONTHLY))
                        .isInstanceOfSatisfying(ApiException.class,
                                e -> assertThat(e.code()).isEqualTo(ErrorCode.PLAN_CHANGE_NOT_ALLOWED));
            }
        }
    }

    @Test
    void theSamePlanAndBillingPeriodIsNotAChange() {
        assertThatThrownBy(() -> PlanChangeRules.checkRequest(
                SubscriptionStatus.ACTIVE, CURRENT, BillingPeriod.MONTHLY, CURRENT, BillingPeriod.MONTHLY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PLAN_CHANGE_NOT_ALLOWED));
        PlanChangeRules.checkRequest(SubscriptionStatus.ACTIVE, CURRENT, BillingPeriod.MONTHLY, CURRENT, BillingPeriod.ANNUAL);
    }

    @Test
    void onlyAnActiveUpgradeOrAnyActiveChangeIsDeferredToTheRenewal() {
        assertThat(PlanChangeRules.appliesAtOnce(SubscriptionStatus.ACTIVE, PlanChangeKind.UPGRADE)).isTrue();
        assertThat(PlanChangeRules.appliesAtOnce(SubscriptionStatus.ACTIVE, PlanChangeKind.DOWNGRADE)).isFalse();
        assertThat(PlanChangeRules.appliesAtOnce(SubscriptionStatus.ACTIVE, PlanChangeKind.PERIOD_CHANGE)).isFalse();
        assertThat(PlanChangeRules.appliesAtOnce(SubscriptionStatus.TRIAL, PlanChangeKind.DOWNGRADE))
                .as("a trial has no contracted period to wait for").isTrue();
        assertThat(PlanChangeRules.appliesAtOnce(SubscriptionStatus.PAST_DUE, PlanChangeKind.DOWNGRADE))
                .as("past due: the renewal date already passed").isTrue();
    }

    private static PlanChangeKind kind(BillingPeriod current, String contracted, BillingPeriod target, String price) {
        return PlanChangeRules.kind(current, new BigDecimal(contracted), target, new BigDecimal(price));
    }
}
