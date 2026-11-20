package com.kuador.medical.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port to the clinics (tenants), implemented by the platform module so that identity does not
 * depend on it.
 */
public interface TenantDirectory {

    Optional<TenantInfo> find(UUID tenantId);

    /**
     * Ids of the active clinics, for platform jobs that process every clinic one by one, each with
     * its own tenant context (for example the adulthood job of MEDK-18).
     */
    List<UUID> activeTenantIds();

    /** A clinic as identity needs it: its name and whether its staff may operate. */
    record TenantInfo(UUID id, String name, boolean active) {}
}
