package com.kuador.medical.shared.tenancy;

import java.util.Objects;
import java.util.UUID;

/**
 * Published by the platform inside the transaction that creates a tenant, with the new tenant
 * bound in {@link TenantContext}. Listeners prepare their tenant data (for example identity seeds
 * the clinic roles) in that same transaction, so a failure leaves nothing behind. It lives in the
 * shared module so that identity can react to it without depending on the platform module.
 */
public record TenantCreated(UUID tenantId) {

    public TenantCreated {
        Objects.requireNonNull(tenantId, "tenantId");
    }
}
