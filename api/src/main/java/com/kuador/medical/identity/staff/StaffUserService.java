package com.kuador.medical.identity.staff;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.PlanLimits;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.ClinicAccountStore;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.account.InvitationService;
import com.kuador.medical.identity.role.AssignmentService;
import com.kuador.medical.identity.role.RoleAssignmentResponse;
import com.kuador.medical.shared.idempotency.IdempotentCreations;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff users of the current clinic (MEDK-16): registration by invitation, edition, disable and
 * enable. Rules (plan of MEDK-12): the email is unique in the clinic (5), a clinical role needs a
 * professional registration (3), the plan user limit is checked in the backend (6), creations are
 * idempotent (7), nobody edits or disables themselves, and only an OWNER disables an OWNER.
 * Every change is audited without personal data.
 */
@Service
class StaffUserService {

    static final String CREATE_OPERATION = "createStaffUser";
    private static final int MAX_PAGE_SIZE = 100;
    /** Staff accounts that take a seat of the plan. */
    private static final List<ClinicAccountStatus> SEATED =
            List.of(ClinicAccountStatus.INVITED, ClinicAccountStatus.PENDING_EMAIL_VERIFICATION, ClinicAccountStatus.ACTIVE);

    private final EntityManager entityManager;
    private final ClinicAccountRepository accounts;
    private final ClinicAccountStore store;
    private final AssignmentService assignments;
    private final InvitationService invitations;
    private final IdempotentCreations idempotency;
    private final PlanLimits planLimits;
    private final AuditRecorder audit;

    StaffUserService(
            EntityManager entityManager,
            ClinicAccountRepository accounts,
            ClinicAccountStore store,
            AssignmentService assignments,
            InvitationService invitations,
            IdempotentCreations idempotency,
            PlanLimits planLimits,
            AuditRecorder audit) {
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.store = store;
        this.assignments = assignments;
        this.invitations = invitations;
        this.idempotency = idempotency;
        this.planLimits = planLimits;
        this.audit = audit;
    }

    /** Filters of the list; every one is optional. */
    record Filter(@Nullable String q, @Nullable ClinicAccountStatus status, @Nullable UUID branchId) {}

    @Transactional(readOnly = true)
    StaffUserPage list(Filter filter, int page, int size) {
        List<FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new FieldViolation("page", "INVALID_FORMAT"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add(new FieldViolation("size", "INVALID_FORMAT"));
        }
        if (filter.q() != null && filter.q().length() > 100) {
            violations.add(new FieldViolation("q", "TOO_LONG"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        Map<String, Object> parameters = new HashMap<>();
        String where = where(filter, parameters);
        TypedQuery<Long> count = entityManager.createQuery("select count(a) from ClinicAccount a" + where, Long.class);
        TypedQuery<ClinicAccount> query = entityManager.createQuery(
                "select a from ClinicAccount a" + where + " order by lower(a.lastNames), lower(a.firstNames), a.id",
                ClinicAccount.class);
        parameters.forEach((name, value) -> {
            count.setParameter(name, value);
            query.setParameter(name, value);
        });
        List<StaffUserResponse> items = query.setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(this::view).toList();
        return new StaffUserPage(page, size, count.getSingleResult(), items);
    }

    @Transactional(readOnly = true)
    StaffUserResponse get(UUID id) {
        return view(staffUser(id));
    }

    @Transactional
    StaffUserResponse create(CreateStaffUserRequest request, @Nullable String idempotencyKey, AuthenticatedAccount caller) {
        IdempotentCreations.Outcome outcome =
                idempotency.create(CREATE_OPERATION, idempotencyKey, request, () -> register(request, caller));
        return view(staffUser(outcome.resourceId()));
    }

    private UUID register(CreateStaffUserRequest request, AuthenticatedAccount caller) {
        if (accounts.existsByEmailInCurrentTenant(EmailAddresses.normalize(request.email()))) {
            throw ClinicAccountStore.emailTaken();
        }
        checkPlanLimit(caller);
        ClinicAccount account = ClinicAccount.invitedStaff(
                request.id() != null ? request.id() : UUID.randomUUID(),
                request.email(), request.firstNames(), request.lastNames(), request.jobTitle(),
                request.professionalRegistration(), caller.accountId());
        store.persistNew(account);
        // Escalation, clinical role and branch rules are those of PUT /staff-users/{id}/role-assignments.
        assignments.replace(account.getId(), request.roleAssignments(), caller);
        audit.record(AuditAction.STAFF_USER_CREATED, caller.tenantId(), caller.accountId(), AuditTarget.account(account.getId()));
        invitations.send(account, caller.accountId());
        return account.getId();
    }

    @Transactional
    StaffUserResponse update(UUID id, StaffUserUpdate update, AuthenticatedAccount caller) {
        ClinicAccount account = staffUser(id);
        ensureNotSelf(id, caller);
        account.ensureVersion(update.version());
        if (update.changesRegistration()
                && (update.professionalRegistration() == null || update.professionalRegistration().isBlank())
                && assignments.holdsClinicalRole(id)) {
            throw ApiException.validation(List.of(new FieldViolation("professionalRegistration", "REQUIRED")));
        }
        account.updatePersonalData(
                update.firstNames(), update.lastNames(), update.jobTitle(),
                update.changesRegistration(), update.professionalRegistration(), update.version(), caller.accountId());
        entityManager.flush();
        audit.record(AuditAction.STAFF_USER_UPDATED, caller.tenantId(), caller.accountId(), AuditTarget.account(id));
        return view(account);
    }

    @Transactional
    void resendInvitation(UUID id, AuthenticatedAccount caller) {
        ClinicAccount account = staffUser(id);
        if (account.getStatus() == ClinicAccountStatus.DISABLED) {
            throw ApiException.validation(List.of(new FieldViolation("status", "ACCOUNT_DISABLED")));
        }
        invitations.send(account, caller.accountId());
    }

    @Transactional
    StaffUserResponse disable(UUID id, AuthenticatedAccount caller) {
        ClinicAccount account = staffUser(id);
        ensureNotSelf(id, caller);
        if (assignments.isOwner(id)) {
            if (!assignments.isOwner(caller.accountId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PRIVILEGE_ESCALATION, "Only an OWNER can disable an OWNER.");
            }
            if (account.isActive() && assignments.activeOwnerCount() <= 1) {
                throw ApiException.conflict(ErrorCode.LAST_OWNER, "The clinic would be left without an OWNER.");
            }
        }
        if (account.getStatus() != ClinicAccountStatus.DISABLED) {
            account.disable(caller.accountId());
            invitations.invalidate(id);
            entityManager.flush();
            audit.record(AuditAction.STAFF_USER_DISABLED, caller.tenantId(), caller.accountId(), AuditTarget.account(id));
        }
        return view(account);
    }

    @Transactional
    StaffUserResponse enable(UUID id, AuthenticatedAccount caller) {
        ClinicAccount account = staffUser(id);
        ensureNotSelf(id, caller);
        if (account.getStatus() == ClinicAccountStatus.DISABLED) {
            checkPlanLimit(caller);
            account.enable(caller.accountId());
            entityManager.flush();
            audit.record(AuditAction.STAFF_USER_ENABLED, caller.tenantId(), caller.accountId(), AuditTarget.account(id));
        }
        return view(account);
    }

    private ClinicAccount staffUser(UUID id) {
        return accounts.findInCurrentTenant(id, AccountKind.STAFF).orElseThrow(ApiException::notFound);
    }

    private static void ensureNotSelf(UUID id, AuthenticatedAccount caller) {
        if (id.equals(caller.accountId())) {
            throw ApiException.conflict(ErrorCode.SELF_MODIFICATION_FORBIDDEN, "Nobody can change their own staff record.");
        }
    }

    private void checkPlanLimit(AuthenticatedAccount caller) {
        OptionalInt limit = planLimits.userLimit(Objects.requireNonNull(caller.tenantId()));
        if (limit.isPresent() && accounts.countByKindAndStatus(AccountKind.STAFF, SEATED) >= limit.getAsInt()) {
            throw ApiException.conflict(
                    ErrorCode.PLAN_LIMIT_REACHED, "The plan allows up to %d users.".formatted(limit.getAsInt()));
        }
    }

    private StaffUserResponse view(ClinicAccount account) {
        List<RoleAssignmentResponse> roleAssignments =
                assignments.assignmentsOf(account.getId()).stream().map(RoleAssignmentResponse::of).toList();
        return new StaffUserResponse(
                account.getId(),
                account.getFirstNames(),
                account.getLastNames(),
                account.getEmail(),
                account.getJobTitle() == null ? "" : account.getJobTitle(),
                account.getProfessionalRegistration(),
                account.getStatus(),
                roleAssignments,
                account.getStatus() == ClinicAccountStatus.INVITED
                        ? invitations.pendingExpiry(account.getId()).orElse(null)
                        : null,
                account.getCreatedAt(),
                account.getVersion());
    }

    /** JPQL filter of the list; @TenantId adds the clinic (ADR-007). */
    private static String where(Filter filter, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder(" where a.kind = :kind");
        parameters.put("kind", AccountKind.STAFF);
        if (filter.status() != null) {
            where.append(" and a.status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            where.append(" and (lower(a.firstNames) like :q escape '\\' or lower(a.lastNames) like :q escape '\\'"
                    + " or a.email like :q escape '\\')");
            parameters.put("q", "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%");
        }
        if (filter.branchId() != null) {
            where.append(" and exists (select 1 from RoleAssignment r where r.userId = a.id and r.branchId = :branchId)");
            parameters.put("branchId", filter.branchId());
        }
        return where.toString();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
