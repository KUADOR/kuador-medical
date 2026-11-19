package com.kuador.medical.identity.auth;

import com.kuador.medical.clinics.BranchDirectory;
import com.kuador.medical.clinics.BranchDirectory.Branch;
import com.kuador.medical.identity.role.EffectivePermissions;
import com.kuador.medical.identity.role.RoleAssignment;
import com.kuador.medical.identity.role.RoleAssignmentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branches an account may operate in the current clinic (ADR-013): every branch for a
 * clinic-wide OWNER, otherwise the branches of its assignments. The clinic must be bound.
 */
@Component
class BranchAccess {

    private final RoleAssignmentRepository assignments;
    private final EffectivePermissions permissions;
    private final BranchDirectory branches;

    BranchAccess(RoleAssignmentRepository assignments, EffectivePermissions permissions, BranchDirectory branches) {
        this.assignments = assignments;
        this.permissions = permissions;
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public List<Branch> available(UUID accountId) {
        if (permissions.of(accountId, null).owner()) {
            return branches.list();
        }
        List<UUID> assigned = assignments.findByUser(accountId).stream()
                .map(RoleAssignment::getBranchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return branches.findAll(assigned).stream()
                .sorted(Comparator.comparing(Branch::name).thenComparing(Branch::id))
                .toList();
    }
}
