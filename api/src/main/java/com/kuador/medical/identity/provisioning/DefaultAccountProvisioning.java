package com.kuador.medical.identity.provisioning;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AccountProvisioning;
import com.kuador.medical.identity.ClinicAccountInfo;
import com.kuador.medical.identity.NewClinicAccount;
import com.kuador.medical.identity.NewTenantOwner;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.ClinicAccountStore;
import com.kuador.medical.identity.account.InvitationService;
import com.kuador.medical.identity.role.Role;
import com.kuador.medical.identity.role.RoleAssignment;
import com.kuador.medical.identity.role.RoleRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultAccountProvisioning implements AccountProvisioning {

    private final EntityManager entityManager;
    private final ClinicAccountRepository accounts;
    private final ClinicAccountStore store;
    private final InvitationService invitations;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roles;
    private final AuditRecorder audit;

    DefaultAccountProvisioning(
            EntityManager entityManager,
            ClinicAccountRepository accounts,
            ClinicAccountStore store,
            InvitationService invitations,
            PasswordEncoder passwordEncoder,
            RoleRepository roles,
            AuditRecorder audit) {
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.store = store;
        this.invitations = invitations;
        this.passwordEncoder = passwordEncoder;
        this.roles = roles;
        this.audit = audit;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID bootstrapOwner(UUID tenantId, NewTenantOwner owner) {
        if (!TenantContext.current().equals(Optional.of(tenantId))) {
            throw new IllegalStateException("The new tenant must be the current tenant");
        }
        // The roles were seeded on TenantCreated; OWNER is clinic-wide (no branch).
        Role ownerRole = roles.findAllInCurrentTenant().stream()
                .filter(Role::isOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The roles of the tenant must be seeded first"));
        ClinicAccount account = ClinicAccount.activeOwner(
                owner.email(), passwordEncoder.encode(owner.password()), owner.displayName());
        store.persistNew(account);
        entityManager.persist(new RoleAssignment(account.getId(), null, ownerRole.getId()));
        entityManager.flush();
        return account.getId();
    }

    @Override
    @Transactional
    public UUID inviteAccount(NewClinicAccount request) {
        ClinicAccount account = ClinicAccount.invited(
                request.kind(), request.email(), request.firstNames(), request.lastNames(), request.patientRef());
        store.persistNew(account);
        audit.record(AuditAction.ACCOUNT_PROVISIONED, account.getTenantId(), null, AuditTarget.account(account.getId()));
        invitations.send(account, null);
        return account.getId();
    }

    @Override
    @Transactional
    public void resendInvitation(UUID accountId) {
        ClinicAccount account = accounts.findInCurrentTenant(accountId).orElseThrow(ApiException::notFound);
        invitations.send(account, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClinicAccountInfo> find(UUID accountId) {
        return accounts.findInCurrentTenant(accountId)
                .map(account -> new ClinicAccountInfo(
                        account.getId(), account.getKind(), account.getStatus(), account.getPatientRef()));
    }
}
