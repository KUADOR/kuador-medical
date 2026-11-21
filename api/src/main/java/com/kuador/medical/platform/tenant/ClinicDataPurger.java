package com.kuador.medical.platform.tenant;

import com.kuador.medical.platform.api.TenantDataPurger;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.Clock;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Erases the contact data of the clinic and its OWNER and marks it purged (ADR-018). Runs last. */
@Component
class ClinicDataPurger implements TenantDataPurger, Ordered {

    private final TenantRepository tenants;
    private final Clock clock = Clock.systemUTC();

    ClinicDataPurger(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public String name() {
        return "platform.clinic";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge() {
        Tenant tenant = tenants.findById(TenantContext.current().orElseThrow()).orElseThrow();
        tenant.purge(clock.instant());
        tenants.flush();
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
