package com.kuador.medical.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Fixed permission catalog of the system (ADR-003, FR-012). Codes are stable: they are stored in
 * {@code role_permission} and published in the contract. Descriptions are shown to people.
 */
public enum Permission {
    ROLES_READ("roles.read", PermissionScope.TENANT, "Ver los roles y el catálogo de permisos asignables"),
    ROLES_CREATE("roles.create", PermissionScope.TENANT, "Crear roles"),
    ROLES_UPDATE("roles.update", PermissionScope.TENANT, "Editar roles"),
    ROLES_DELETE("roles.delete", PermissionScope.TENANT, "Eliminar roles"),
    PLATFORM_PERMISSIONS_READ(
            "platform.permissions.read", PermissionScope.PLATFORM, "Ver el catálogo completo de permisos");

    private final String code;
    private final PermissionScope scope;
    private final String description;

    Permission(String code, PermissionScope scope, String description) {
        this.code = code;
        this.scope = scope;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public PermissionScope scope() {
        return scope;
    }

    public String description() {
        return description;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(permission -> permission.code.equals(code)).findFirst();
    }

    /** Permissions of the scope, in catalog order. */
    public static List<Permission> ofScope(PermissionScope scope) {
        return Arrays.stream(values()).filter(permission -> permission.scope == scope).toList();
    }
}
