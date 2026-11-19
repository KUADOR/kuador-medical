package com.kuador.medical.identity.provisioning;

import com.kuador.medical.identity.NewTenantOwner;
import com.kuador.medical.identity.TenantOwnerProvisioning;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import com.kuador.medical.identity.membership.TenantMembership;
import com.kuador.medical.identity.role.Role;
import com.kuador.medical.identity.role.RoleAssignment;
import com.kuador.medical.identity.role.RoleRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultTenantOwnerProvisioning implements TenantOwnerProvisioning {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "user_account_email_unique";

    private final EntityManager entityManager;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roles;

    DefaultTenantOwnerProvisioning(
            EntityManager entityManager,
            UserAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            RoleRepository roles) {
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.roles = roles;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID provisionOwner(UUID tenantId, NewTenantOwner owner) {
        if (!TenantContext.current().equals(Optional.of(tenantId))) {
            throw new IllegalStateException("The new tenant must be the current tenant");
        }
        String email = EmailAddresses.normalize(owner.email());
        if (accounts.existsByEmail(email)) {
            throw emailTaken();
        }
        // The roles were seeded on TenantCreated; OWNER is clinic-wide (no branch). Looked up before
        // persisting the account, so the query does not auto-flush it outside the translation below.
        Role ownerRole = roles.findAllInCurrentTenant().stream()
                .filter(Role::isOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The roles of the tenant must be seeded first"));
        UserAccount account = UserAccount.clinicStaff(email, passwordEncoder.encode(owner.password()), owner.displayName());
        entityManager.persist(account);
        TenantMembership membership = new TenantMembership(account.getId());
        entityManager.persist(membership);
        flushTranslatingDuplicateEmail();
        entityManager.persist(new RoleAssignment(account.getId(), null, ownerRole.getId()));
        entityManager.flush();
        return account.getId();
    }

    /** Two signups racing for the same email: the unique constraint decides, the loser gets 409. */
    private void flushTranslatingDuplicateEmail() {
        try {
            entityManager.flush();
        } catch (PersistenceException e) {
            if (e instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                throw emailTaken();
            }
            throw e;
        }
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.EMAIL_TAKEN, "The email is already registered.");
    }
}
