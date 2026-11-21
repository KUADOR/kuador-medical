package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.PlanPriceChanged;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inbound events of the subscription (FR-019): {@link PaymentReceived} and {@link PaymentFailed}
 * from the billing epic (plan D5) and {@link PlanPriceChanged} from the plans (plan D1). Each one is
 * handled after the publisher commits, in its own transaction with the clinic bound in the tenant
 * context, so a failure here never undoes the publisher's work; it is logged with identifiers only.
 */
@Component
class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final SubscriptionLifecycle lifecycle;
    private final SubscriptionDirectory directory;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    PaymentEventsListener(
            SubscriptionLifecycle lifecycle, SubscriptionDirectory directory, PlatformTransactionManager transactionManager) {
        this.lifecycle = lifecycle;
        this.directory = directory;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(PaymentReceived payment) {
        inClinic(payment.tenantId(), "PaymentReceived", () -> lifecycle.paymentReceived(payment, clock.instant()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(PaymentFailed payment) {
        inClinic(payment.tenantId(), "PaymentFailed", () -> lifecycle.paymentFailed(payment));
    }

    /** Notifies at once the clinics renewing on the plan; the lifecycle job retries any that fails. */
    @TransactionalEventListener(fallbackExecution = true)
    void on(PlanPriceChanged change) {
        for (UUID tenantId : directory.tenantsRenewing(change.planId(), change.billingPeriod())) {
            inClinic(tenantId, "PlanPriceChanged", () -> lifecycle.noticePriceChange(clock.instant()));
        }
    }

    private void inClinic(UUID tenantId, String event, Runnable work) {
        try {
            TenantContext.runAs(tenantId, () -> transaction.executeWithoutResult(status -> work.run()));
        } catch (RuntimeException e) {
            log.error("{} could not be applied to tenant {}", event, tenantId, e);
        }
    }
}
