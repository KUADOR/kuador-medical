package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.plan.BillingPeriod;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Platform-level reads of which clinics the lifecycle has to visit. They return clinic ids only;
 * every clinic is then processed with its own tenant context (like the adulthood job, exception 2
 * to principle I of the plan of MEDK-12: it iterates clinics but never mixes their data).
 */
@Component
class SubscriptionDirectory {

    private final JdbcTemplate jdbc;

    SubscriptionDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Clinics whose subscription can still reach a deadline or a renewal (not cancelled, not purged). */
    List<UUID> tenantsInLifecycle() {
        return jdbc.queryForList("""
                select s.tenant_id from subscription s join tenant t on t.id = s.tenant_id
                where s.status <> 'CANCELLED' and t.purged_at is null
                order by s.tenant_id""", UUID.class);
    }

    /** Clinics with a renewal pending on the plan and billing period of a new price (plan D1). */
    List<UUID> tenantsRenewing(UUID planId, BillingPeriod billingPeriod) {
        return jdbc.queryForList("""
                select s.tenant_id from subscription s join tenant t on t.id = s.tenant_id
                where s.plan_id = ? and s.billing_period = ? and s.status in ('ACTIVE', 'PAST_DUE')
                  and t.purged_at is null
                order by s.tenant_id""", UUID.class, planId, billingPeriod.name());
    }
}
