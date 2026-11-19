package com.kuador.medical.identity.role;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permissions a user holds in the current clinic (ADR-013): those of its clinic-wide assignment
 * (OWNER) plus those of its assignment in the given branch. Read from the database on every
 * call, so a change of role applies to the next request (ADR-008).
 */
@Component
public class EffectivePermissions {

    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final RoleAssignmentRepository assignments;

    EffectivePermissions(
            RoleRepository roles, RolePermissionRepository rolePermissions, RoleAssignmentRepository assignments) {
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.assignments = assignments;
    }

    /** Roles and permissions of a user in a branch ({@code null}: clinic-wide assignments only). */
    public record Grant(List<String> roleNames, Set<Permission> permissions, boolean owner) {

        public Grant {
            roleNames = List.copyOf(roleNames);
            permissions = Set.copyOf(permissions);
        }

        /** Permission codes in catalog order. */
        public List<String> permissionCodes() {
            return Permission.ofScope(PermissionScope.TENANT).stream()
                    .filter(permissions::contains)
                    .map(Permission::code)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public Grant of(UUID userId, @Nullable UUID branchId) {
        List<UUID> roleIds = assignments.findByUser(userId).stream()
                .filter(assignment -> assignment.getBranchId() == null || Objects.equals(assignment.getBranchId(), branchId))
                .map(RoleAssignment::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new Grant(List.of(), Set.of(), false);
        }
        List<Role> held = roles.findAllInCurrentTenant(roleIds);
        Set<Permission> permissions = rolePermissions.findByRoleIdIn(roleIds).stream()
                .map(granted -> Permission.fromCode(granted.getPermissionCode()))
                .flatMap(Optional::stream)
                .filter(permission -> permission.scope() == PermissionScope.TENANT)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
        List<String> names = held.stream().map(Role::getName).sorted(Comparator.naturalOrder()).toList();
        return new Grant(names, permissions, held.stream().anyMatch(Role::isOwner));
    }

    /** The caller as {@link EscalationGuard} sees it. */
    @Transactional(readOnly = true)
    public EscalationGuard.Actor actor(AuthenticatedAccount caller) {
        Set<Permission> current = caller.permissions().stream()
                .map(Permission::fromCode)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        boolean owner = of(caller.accountId(), null).owner();
        return new EscalationGuard.Actor(
                caller.accountId(), owner, branchId -> of(caller.accountId(), branchId).permissions(), current);
    }
}
