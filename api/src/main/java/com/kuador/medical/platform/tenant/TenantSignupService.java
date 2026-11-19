package com.kuador.medical.platform.tenant;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.NewTenantOwner;
import com.kuador.medical.identity.TenantOwnerProvisioning;
import com.kuador.medical.shared.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Clinic self-signup (FR-015). The tenant, its owner, the membership and the roles OWNER and
 * ADMIN are created in one transaction: a failure leaves nothing behind. The new tenant is bound
 * before the transaction opens so tenant-scoped rows take it (ADR-007).
 */
@Service
class TenantSignupService {

    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final TenantOwnerProvisioning ownerProvisioning;
    private final AuditRecorder audit;

    TenantSignupService(
            EntityManager entityManager,
            TransactionTemplate transaction,
            TenantOwnerProvisioning ownerProvisioning,
            AuditRecorder audit) {
        this.entityManager = entityManager;
        this.transaction = transaction;
        this.ownerProvisioning = ownerProvisioning;
        this.audit = audit;
    }

    UUID signUp(TenantSignupRequest request) {
        UUID tenantId = UUID.randomUUID();
        return TenantContext.callAs(tenantId, () -> transaction.execute(status -> {
            entityManager.persist(new Tenant(tenantId, request.clinicName().strip()));
            entityManager.flush();
            UUID ownerId = ownerProvisioning.provisionOwner(tenantId, new NewTenantOwner(
                    request.ownerDisplayName().strip(), request.email(), request.password()));
            audit.record(AuditAction.TENANT_SIGNED_UP, tenantId, ownerId, AuditTarget.tenant(tenantId));
            return tenantId;
        }));
    }
}
