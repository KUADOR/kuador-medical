package com.kuador.medical.platform.tenant;

import com.kuador.medical.identity.TenantDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gives identity the clinics it needs (name, active) without depending on the platform module. */
@Component
class PlatformTenantDirectory implements TenantDirectory {

    private final TenantRepository tenants;

    PlatformTenantDirectory(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantInfo> find(UUID tenantId) {
        return tenants.findById(tenantId).map(tenant -> new TenantInfo(tenant.getId(), tenant.getName(), tenant.isActive()));
    }
}
