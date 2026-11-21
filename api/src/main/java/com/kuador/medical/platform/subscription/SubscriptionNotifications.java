package com.kuador.medical.platform.subscription;

import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.notifications.PlatformEmails;
import com.kuador.medical.platform.plan.BillingPeriod;
import com.kuador.medical.platform.tenant.Tenant;
import com.kuador.medical.platform.tenant.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Emails of the subscription lifecycle to the OWNER of the clinic (Spanish templates of
 * {@link PlatformEmails}). They are sent after the transaction commits, so a rolled-back change
 * never notifies; a failed send is logged with identifiers only and does not undo the change.
 */
@Component
class SubscriptionNotifications {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionNotifications.class);

    private final TenantRepository tenants;
    private final NotificationSender sender;

    SubscriptionNotifications(TenantRepository tenants, NotificationSender sender) {
        this.tenants = tenants;
        this.sender = sender;
    }

    void statusChanged(UUID tenantId, Subscription subscription, SubscriptionStatus from, String planName,
            int suspensionDays) {
        switch (subscription.getStatus()) {
            case PAST_DUE -> send(tenantId, "PAST_DUE", (to, clinic) -> PlatformEmails.subscriptionOverdue(
                    to, clinic, localDate(subscription.getGraceEndsAt())));
            case SUSPENDED -> send(tenantId, "SUSPENDED", (to, clinic) -> PlatformEmails.subscriptionSuspended(
                    to, clinic, localDate(LifecycleDates.daysAfter(subscription.getSuspendedAt(), suspensionDays))));
            case CANCELLED -> send(tenantId, "CANCELLED", (to, clinic) -> PlatformEmails.subscriptionCancelled(
                    to, clinic, localDate(subscription.getRetentionEndsAt())));
            case ACTIVE -> {
                if (from == SubscriptionStatus.SUSPENDED || from == SubscriptionStatus.CANCELLED) {
                    send(tenantId, "REACTIVATED",
                            (to, clinic) -> PlatformEmails.subscriptionReactivated(to, clinic, planName));
                }
            }
            case TRIAL -> {
                // No email: the clinic starts in TRIAL only at onboarding.
            }
        }
    }

    void priceChange(UUID tenantId, String planName, BillingPeriod billingPeriod, BigDecimal currentAmount,
            BigDecimal newAmount, String currency, Instant appliesFrom) {
        send(tenantId, "PRICE_CHANGE", (to, clinic) -> PlatformEmails.priceChangeNotice(to, clinic, planName,
                PlatformEmails.BillingPeriod.valueOf(billingPeriod.name()), currentAmount, newAmount, currency,
                localDate(appliesFrom)));
    }

    private void send(UUID tenantId, String kind, BiFunction<String, String, EmailMessage> template) {
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getOwnerEmail() == null) {
            // Clinics of the earlier self-signup have no OWNER email to notify.
            return;
        }
        EmailMessage email = template.apply(tenant.getOwnerEmail(), tenant.getName());
        Runnable delivery = () -> {
            try {
                sender.sendEmail(email);
            } catch (RuntimeException e) {
                log.warn("Subscription email {} could not be sent for tenant {}", kind, tenantId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
        } else {
            delivery.run();
        }
    }

    private static LocalDate localDate(Instant instant) {
        return LocalDate.ofInstant(instant, LifecycleDates.ZONE);
    }
}
