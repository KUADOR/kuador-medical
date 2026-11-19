package com.kuador.medical.identity.role;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.identity.membership.MembershipRoleRepository;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD of the roles of the caller's clinic (FR-006). The clinic is never a parameter: every
 * query runs in the tenant bound from the token (ADR-007), so a role of another clinic is simply
 * not found (404). Changes are audited in the same transaction (FR-011).
 */
@Service
public class RoleService {

    private static final String NAME_UNIQUE_INDEX = "role_tenant_name_unique";
    private static final String ROLE_IN_USE_FK = "membership_role_role_fk";

    /** OWNER, ADMIN, then the clinic roles by name. */
    private static final Comparator<Role> LISTING_ORDER = Comparator
            .comparing((Role role) -> role.getSystemCode() == null ? Integer.MAX_VALUE : role.getSystemCode().ordinal())
            .thenComparing(role -> role.getName().toLowerCase())
            .thenComparing(Role::getId);

    private final EntityManager entityManager;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final MembershipRoleRepository membershipRoles;
    private final AuditRecorder audit;

    RoleService(
            EntityManager entityManager,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            MembershipRoleRepository membershipRoles,
            AuditRecorder audit) {
        this.entityManager = entityManager;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.membershipRoles = membershipRoles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        List<Role> found = roles.findAllInCurrentTenant().stream().sorted(LISTING_ORDER).toList();
        Map<UUID, Set<String>> granted = rolePermissions.findByRoleIdIn(found.stream().map(Role::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        RolePermission::getRoleId, Collectors.mapping(RolePermission::getPermissionCode, Collectors.toSet())));
        return found.stream().map(role -> view(role, granted.getOrDefault(role.getId(), Set.of()))).toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public RoleView create(RoleCommand command, AuthenticatedAccount actor) {
        List<Permission> permissions = AssignablePermissions.parse(command.permissionCodes());
        if (roles.existsByName(command.name().strip())) {
            throw nameTaken();
        }
        Role role = Role.custom(command.name(), command.description());
        entityManager.persist(role);
        permissions.forEach(permission -> entityManager.persist(new RolePermission(role.getId(), permission.code())));
        flush();
        audit.record(AuditAction.ROLE_CREATED, actor.tenantId(), actor.accountId(), AuditTarget.role(role.getId()));
        return new RoleView(role.getId(), role.getName(), role.getDescription(), false, permissions, role.getVersion());
    }

    @Transactional
    public RoleView update(UUID id, RoleCommand command, long expectedVersion, AuthenticatedAccount actor) {
        Role role = find(id);
        role.ensureUpdatable(expectedVersion);
        List<Permission> permissions = AssignablePermissions.parse(command.permissionCodes());
        // Checked before changing the entity: the query would otherwise auto-flush the new name.
        if (roles.existsByNameExcluding(command.name().strip(), role.getId())) {
            throw nameTaken();
        }
        role.update(command.name(), command.description(), expectedVersion);
        replacePermissions(role, permissions);
        flush();
        audit.record(AuditAction.ROLE_UPDATED, actor.tenantId(), actor.accountId(), AuditTarget.role(role.getId()));
        return new RoleView(role.getId(), role.getName(), role.getDescription(), false, permissions, role.getVersion());
    }

    @Transactional
    public void delete(UUID id, AuthenticatedAccount actor) {
        Role role = find(id);
        role.ensureDeletable();
        if (membershipRoles.existsByRoleId(role.getId())) {
            throw roleInUse();
        }
        rolePermissions.deleteAll(rolePermissions.findByRoleIdIn(List.of(role.getId())));
        entityManager.remove(role);
        flush();
        audit.record(AuditAction.ROLE_DELETED, actor.tenantId(), actor.accountId(), AuditTarget.role(role.getId()));
    }

    private Role find(UUID id) {
        return roles.findInCurrentTenant(id).orElseThrow(ApiException::notFound);
    }

    /** Only the difference is written: Hibernate inserts before it deletes, so a re-added code must not be removed. */
    private void replacePermissions(Role role, List<Permission> permissions) {
        Set<String> wanted = permissions.stream().map(Permission::code).collect(Collectors.toSet());
        List<RolePermission> current = rolePermissions.findByRoleIdIn(List.of(role.getId()));
        Set<String> kept = current.stream().map(RolePermission::getPermissionCode).collect(Collectors.toSet());
        current.stream().filter(granted -> !wanted.contains(granted.getPermissionCode())).forEach(entityManager::remove);
        wanted.stream()
                .filter(code -> !kept.contains(code))
                .forEach(code -> entityManager.persist(new RolePermission(role.getId(), code)));
    }

    private RoleView view(Role role) {
        Set<String> granted = rolePermissions.findByRoleIdIn(List.of(role.getId())).stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toSet());
        return view(role, granted);
    }

    private static RoleView view(Role role, Set<String> grantedCodes) {
        List<Permission> permissions = Permission.ofScope(PermissionScope.TENANT).stream()
                .filter(permission -> grantedCodes.contains(permission.code()))
                .toList();
        return new RoleView(
                role.getId(), role.getName(), role.getDescription(), role.isSystemRole(), permissions, role.getVersion());
    }

    /**
     * Races the pre-checks cannot see are decided by the database: a concurrent rename (unique
     * name), a concurrent update (version) and a concurrent assignment (foreign key).
     */
    private void flush() {
        try {
            entityManager.flush();
        } catch (OptimisticLockException e) {
            throw versionConflict();
        } catch (PersistenceException e) {
            String constraint = violatedConstraint(e);
            if (NAME_UNIQUE_INDEX.equals(constraint)) {
                throw nameTaken();
            }
            if (ROLE_IN_USE_FK.equals(constraint)) {
                throw roleInUse();
            }
            throw e;
        }
    }

    private static @Nullable String violatedConstraint(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    private static ApiException nameTaken() {
        return ApiException.conflict(ErrorCode.ROLE_NAME_TAKEN, "Another role of the clinic has this name.");
    }

    private static ApiException roleInUse() {
        return ApiException.conflict(ErrorCode.ROLE_IN_USE, "The role is assigned to a member of the clinic.");
    }

    private static ApiException versionConflict() {
        return ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The role was changed by someone else.");
    }
}
