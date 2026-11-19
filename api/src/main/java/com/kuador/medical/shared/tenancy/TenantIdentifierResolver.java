package com.kuador.medical.shared.tenancy;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Feeds Hibernate {@code @TenantId} with the {@link TenantContext}. Without a tenant it returns
 * {@link #NO_TENANT}, a value no tenant has: queries see nothing and inserts fail (fail closed).
 */
public final class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Sentinel used when no tenant is bound. Never assigned to a real tenant. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current().orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return false;
    }
}
