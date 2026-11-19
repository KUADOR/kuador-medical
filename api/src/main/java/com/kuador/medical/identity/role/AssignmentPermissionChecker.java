package com.kuador.medical.identity.role;

import com.kuador.medical.identity.AccountScope;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionChecker;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.shared.web.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link PermissionChecker} backed by the caller and its branch role assignments. */
@Component
class AssignmentPermissionChecker implements PermissionChecker {

    private final CurrentUser currentUser;
    private final EffectivePermissions effectivePermissions;

    AssignmentPermissionChecker(CurrentUser currentUser, EffectivePermissions effectivePermissions) {
        this.currentUser = currentUser;
        this.effectivePermissions = effectivePermissions;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return currentUser.current().map(caller -> caller.hasPermission(permission.code())).orElse(false);
    }

    @Override
    public boolean hasPermissionInBranch(Permission permission, UUID branchId) {
        Optional<AuthenticatedAccount> caller = currentUser.current()
                .filter(found -> found.scope() == AccountScope.TENANT);
        if (caller.isEmpty() || permission.scope() != PermissionScope.TENANT) {
            return false;
        }
        return effectivePermissions.of(caller.get().accountId(), branchId).permissions().contains(permission);
    }

    @Override
    public void requirePermission(Permission permission) {
        if (currentUser.current().isEmpty()) {
            throw ApiException.unauthenticated();
        }
        if (!hasPermission(permission)) {
            throw ApiException.forbidden("Access denied.");
        }
    }
}
