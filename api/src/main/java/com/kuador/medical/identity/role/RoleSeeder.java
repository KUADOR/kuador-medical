package com.kuador.medical.identity.role;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.tenancy.TenantCreated;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the roles of a new clinic from {@link RoleSeed} when the platform publishes
 * {@link TenantCreated}. It runs synchronously in the transaction that creates the tenant, so the
 * owner can be given the OWNER role right after, and is idempotent: a role whose key already
 * exists in the clinic is left as it is.
 */
@Component
class RoleSeeder {

    private final EntityManager entityManager;
    private final RoleRepository roles;
    private final AuditRecorder audit;
    private final RoleSeed seed;

    RoleSeeder(EntityManager entityManager, RoleRepository roles, AuditRecorder audit) {
        this.entityManager = entityManager;
        this.roles = roles;
        this.audit = audit;
        this.seed = RoleSeed.load();
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TenantCreated event) {
        if (!TenantContext.current().equals(Optional.of(event.tenantId()))) {
            throw new IllegalStateException("The new tenant must be the current tenant");
        }
        Set<String> existing = roles.findAllInCurrentTenant().stream()
                .map(Role::getSuggestedKey)
                .filter(key -> key != null)
                .collect(Collectors.toSet());
        for (RoleSeed.SeededRole seeded : seed.roles()) {
            if (existing.contains(seeded.key())) {
                continue;
            }
            Role role = seeded.system() != null
                    ? Role.system(seeded.system(), seeded.name(), seeded.description())
                    : Role.suggested(seeded.key(), seeded.name(), seeded.description(), seeded.clinical());
            entityManager.persist(role);
            seeded.permissions().forEach(permission ->
                    entityManager.persist(new RolePermission(role.getId(), permission.code())));
        }
        entityManager.flush();
        audit.record(AuditAction.ROLES_SEEDED, event.tenantId(), null, AuditTarget.tenant(event.tenantId()));
    }
}
