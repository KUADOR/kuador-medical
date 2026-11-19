package com.kuador.medical.shared.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant of the work in progress (ADR-007). It is bound only by the backend (from the access
 * token, or by a platform service acting on a tenant it just created), never from client input,
 * and only for the duration of a scope.
 */
public final class TenantContext {

    private static final ScopedValue<UUID> CURRENT = ScopedValue.newInstance();

    private TenantContext() {}

    /** The tenant bound to the current scope, if any. */
    public static Optional<UUID> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /** Runs {@code work} with {@code tenantId} as the current tenant. */
    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        Objects.requireNonNull(tenantId, "tenantId");
        return ScopedValue.where(CURRENT, tenantId).call(work::get);
    }

    /** Runs {@code work} with {@code tenantId} as the current tenant. */
    public static void runAs(UUID tenantId, Runnable work) {
        Objects.requireNonNull(tenantId, "tenantId");
        ScopedValue.where(CURRENT, tenantId).run(work);
    }
}
