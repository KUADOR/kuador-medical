package com.kuador.medical.identity.role;

/**
 * Order of the identity listeners of {@link com.kuador.medical.shared.tenancy.TenantCreated}: the
 * roles are seeded first, then the accounts that need them (the invited OWNER, MEDK-67).
 */
public final class RoleSeedingOrder {

    public static final int SEED = 0;
    public static final int AFTER = 100;

    private RoleSeedingOrder() {}
}
