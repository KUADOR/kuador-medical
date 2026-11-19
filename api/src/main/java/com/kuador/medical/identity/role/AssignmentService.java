package com.kuador.medical.identity.role;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.clinics.BranchDirectory;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.membership.TenantMembershipRepository;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branch role assignments of the staff of the current clinic (ADR-013, rules 1 and 2 of the plan
 * of MEDK-12). {@code ALL_BRANCHES} creates one assignment per existing branch; branches created
 * later are not assigned automatically. OWNER is clinic-wide (one assignment without branch).
 * Every replacement is checked by {@link EscalationGuard} and audited.
 */
@Service
public class AssignmentService {

    static final String UNKNOWN_ROLE = "UNKNOWN_ROLE";
    static final String UNKNOWN_BRANCH = "UNKNOWN_BRANCH";
    static final String DUPLICATE = "DUPLICATE";
    static final String REQUIRED = "REQUIRED";
    static final String NOT_ALLOWED = "NOT_ALLOWED";
    /** OWNER is clinic-wide: it is assigned with {@code ALL_BRANCHES} only. */
    static final String OWNER_REQUIRES_ALL_BRANCHES = "OWNER_REQUIRES_ALL_BRANCHES";

    private final EntityManager entityManager;
    private final TenantMembershipRepository memberships;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final RoleAssignmentRepository assignments;
    private final BranchDirectory branches;
    private final EffectivePermissions effectivePermissions;
    private final AuditRecorder audit;

    AssignmentService(
            EntityManager entityManager,
            TenantMembershipRepository memberships,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            RoleAssignmentRepository assignments,
            BranchDirectory branches,
            EffectivePermissions effectivePermissions,
            AuditRecorder audit) {
        this.entityManager = entityManager;
        this.memberships = memberships;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.assignments = assignments;
        this.branches = branches;
        this.effectivePermissions = effectivePermissions;
        this.audit = audit;
    }

    /** Current assignments of a user of the current clinic, clinic-wide first, then by branch name. */
    @Transactional(readOnly = true)
    public List<AssignmentView> assignmentsOf(UUID userId) {
        List<RoleAssignment> found = assignments.findByUser(userId);
        Map<UUID, Role> rolesById = rolesById(found.stream().map(RoleAssignment::getRoleId).toList());
        Map<UUID, BranchDirectory.Branch> branchesById = branches.findAll(found.stream()
                        .map(RoleAssignment::getBranchId).filter(id -> id != null).toList())
                .stream().collect(Collectors.toMap(BranchDirectory.Branch::id, Function.identity()));
        return found.stream()
                .map(assignment -> {
                    Role role = rolesById.get(assignment.getRoleId());
                    BranchDirectory.Branch branch =
                            assignment.getBranchId() == null ? null : branchesById.get(assignment.getBranchId());
                    return new AssignmentView(
                            assignment.getBranchId(), branch == null ? null : branch.name(), role.getId(), role.getName());
                })
                .sorted(Comparator.comparing((AssignmentView view) -> view.branchId() != null)
                        .thenComparing(view -> view.branchName() == null ? "" : view.branchName().toLowerCase())
                        .thenComparing(view -> String.valueOf(view.branchId())))
                .toList();
    }

    @Transactional
    List<AssignmentView> replace(UUID userId, RoleAssignmentsRequest request, AuthenticatedAccount caller) {
        memberships.findByUserAccountId(userId).orElseThrow(ApiException::notFound);
        List<EscalationGuard.Slot> requested = requestedSlots(request);
        List<RoleAssignment> current = assignments.findByUser(userId);
        Map<UUID, Role> currentRoles = rolesById(current.stream().map(RoleAssignment::getRoleId).toList());
        List<EscalationGuard.Slot> currentSlots = current.stream()
                .map(assignment -> new EscalationGuard.Slot(
                        assignment.getBranchId(), guarded(currentRoles.get(assignment.getRoleId()))))
                .toList();
        EscalationGuard.checkAssignmentReplacement(
                effectivePermissions.actor(caller), userId, currentSlots, requested, activeOwners());

        current.forEach(entityManager::remove);
        // Deletions first: Hibernate would otherwise insert before deleting and hit the unique key.
        entityManager.flush();
        requested.forEach(slot -> entityManager.persist(new RoleAssignment(userId, slot.branchId(), slot.role().id())));
        entityManager.flush();
        audit.record(AuditAction.ROLE_ASSIGNMENTS_REPLACED, caller.tenantId(), caller.accountId(), AuditTarget.account(userId));
        return assignmentsOf(userId);
    }

    private List<EscalationGuard.Slot> requestedSlots(RoleAssignmentsRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        List<EscalationGuard.Slot> slots = switch (request.mode()) {
            case ALL_BRANCHES -> allBranches(request, violations);
            case PER_BRANCH -> perBranch(request, violations);
        };
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        return slots;
    }

    private List<EscalationGuard.Slot> allBranches(RoleAssignmentsRequest request, List<FieldViolation> violations) {
        if (request.assignments() != null) {
            violations.add(new FieldViolation("assignments", NOT_ALLOWED));
        }
        if (request.roleId() == null) {
            violations.add(new FieldViolation("roleId", REQUIRED));
            return List.of();
        }
        Optional<Role> role = roles.findInCurrentTenant(request.roleId());
        if (role.isEmpty()) {
            violations.add(new FieldViolation("roleId", UNKNOWN_ROLE));
            return List.of();
        }
        EscalationGuard.GuardedRole guarded = guarded(role.get());
        if (role.get().isOwner()) {
            return List.of(new EscalationGuard.Slot(null, guarded));
        }
        return branches.list().stream().map(branch -> new EscalationGuard.Slot(branch.id(), guarded)).toList();
    }

    private List<EscalationGuard.Slot> perBranch(RoleAssignmentsRequest request, List<FieldViolation> violations) {
        if (request.roleId() != null) {
            violations.add(new FieldViolation("roleId", NOT_ALLOWED));
        }
        List<RoleAssignmentsRequest.BranchRole> requested = request.assignments();
        if (requested == null || requested.isEmpty()) {
            violations.add(new FieldViolation("assignments", REQUIRED));
            return List.of();
        }
        Map<UUID, Role> rolesById = rolesById(requested.stream().map(RoleAssignmentsRequest.BranchRole::roleId).toList());
        Set<UUID> knownBranches = branches.findAll(requested.stream().map(RoleAssignmentsRequest.BranchRole::branchId).toList())
                .stream().map(BranchDirectory.Branch::id).collect(Collectors.toSet());
        Set<UUID> seenBranches = new HashSet<>();
        Map<UUID, EscalationGuard.GuardedRole> guardedRoles = new HashMap<>();
        List<EscalationGuard.Slot> slots = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            RoleAssignmentsRequest.BranchRole entry = requested.get(index);
            String field = "assignments[" + index + "]";
            if (!knownBranches.contains(entry.branchId())) {
                violations.add(new FieldViolation(field + ".branchId", UNKNOWN_BRANCH));
            } else if (!seenBranches.add(entry.branchId())) {
                violations.add(new FieldViolation(field + ".branchId", DUPLICATE));
            }
            Role role = rolesById.get(entry.roleId());
            if (role == null) {
                violations.add(new FieldViolation(field + ".roleId", UNKNOWN_ROLE));
            } else if (role.isOwner()) {
                violations.add(new FieldViolation(field + ".roleId", OWNER_REQUIRES_ALL_BRANCHES));
            } else {
                slots.add(new EscalationGuard.Slot(entry.branchId(), guardedRoles.computeIfAbsent(role.getId(), id -> guarded(role))));
            }
        }
        return slots;
    }

    private long activeOwners() {
        List<UUID> ownerRoles = roles.findAllInCurrentTenant().stream().filter(Role::isOwner).map(Role::getId).toList();
        return ownerRoles.isEmpty() ? 0 : assignments.countActiveUsersWithRoles(ownerRoles);
    }

    private Map<UUID, Role> rolesById(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return roles.findAllInCurrentTenant(Set.copyOf(ids)).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
    }

    private EscalationGuard.GuardedRole guarded(Role role) {
        Set<Permission> permissions = rolePermissions.findByRoleIdIn(List.of(role.getId())).stream()
                .map(granted -> Permission.fromCode(granted.getPermissionCode()))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        return new EscalationGuard.GuardedRole(role.getId(), role.getSystemCode(), permissions);
    }
}
