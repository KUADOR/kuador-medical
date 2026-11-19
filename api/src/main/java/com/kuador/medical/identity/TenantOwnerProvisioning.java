package com.kuador.medical.identity;

import java.util.UUID;

/**
 * Creates the owner of a clinic that is being signed up: the account, its membership and the
 * system roles OWNER and ADMIN with every clinic permission, both assigned to the owner (FR-009).
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
