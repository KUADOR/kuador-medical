package com.kuador.medical.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Port to the clinics (tenants), implemented by the platform module so that identity does not
 * depend on it.
 */
public interface TenantDirectory {

    Optional<TenantInfo> find(UUID tenantId);

    /** A clinic as identity needs it: its name and whether its staff may operate. */
    record TenantInfo(UUID id, String name, boolean active) {}
}
