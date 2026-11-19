package com.kuador.medical.identity;

import java.util.UUID;

/**
 * Creates the owner of a clinic that is being signed up: the account, its membership and its
 * clinic-wide OWNER assignment (FR-009). The roles must already be seeded, which identity does
 * when the platform publishes {@link com.kuador.medical.shared.tenancy.TenantCreated}.
 */
public interface TenantOwnerProvisioning {

    /**
     * Must run inside the transaction that creates the tenant, with {@code tenantId} bound as the
     * current tenant, so that a failure leaves neither the tenant nor its owner.
     *
     * @return id of the owner account
     * @throws com.kuador.medical.shared.web.ApiException {@code EMAIL_TAKEN} when the email is registered
     */
    UUID provisionOwner(UUID tenantId, NewTenantOwner owner);
}
