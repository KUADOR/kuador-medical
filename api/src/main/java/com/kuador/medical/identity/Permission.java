package com.kuador.medical.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Fixed permission catalog of the system (ADR-003, FR-012). Codes are stable: they are stored in
 * {@code role_permission} and published in the contract ({@code PermissionCode}). Descriptions are
 * shown to people. Never rename a code: add a new one and migrate the stored roles instead.
 */
public enum Permission {
    ROLES_READ("roles.read", PermissionScope.TENANT, "Ver los roles y el catálogo de permisos asignables"),
    ROLES_CREATE("roles.create", PermissionScope.TENANT, "Crear roles"),
    ROLES_UPDATE("roles.update", PermissionScope.TENANT, "Editar roles"),
    ROLES_DELETE("roles.delete", PermissionScope.TENANT, "Eliminar roles"),
    USERS_READ("identity.users.read", PermissionScope.TENANT, "Ver el personal de la clínica"),
    USERS_MANAGE(
            "identity.users.manage",
            PermissionScope.TENANT,
            "Registrar, invitar, editar, desactivar y reactivar al personal"),
    ASSIGNMENTS_MANAGE(
            "identity.assignments.manage", PermissionScope.TENANT, "Asignar roles al personal por sucursal"),
    PATIENTS_READ("patients.read", PermissionScope.TENANT, "Ver pacientes"),
    PATIENTS_REGISTER(
            "patients.register",
            PermissionScope.TENANT,
            "Registrar pacientes, su consentimiento e invitarlos a activar su cuenta"),
    PATIENTS_EDIT("patients.edit", PermissionScope.TENANT, "Editar los datos de los pacientes"),
    PATIENTS_GUARDIANS_MANAGE(
            "patients.guardians.manage", PermissionScope.TENANT, "Gestionar representantes y sus vínculos"),
    PATIENTS_SELF_REGISTRATION_CONFIGURE(
            "patients.selfRegistration.configure",
            PermissionScope.TENANT,
            "Habilitar o deshabilitar el autorregistro de pacientes"),
    PLATFORM_PERMISSIONS_READ(
            "platform.permissions.read", PermissionScope.PLATFORM, "Ver el catálogo completo de permisos"),
    // MEDK-65 (epic MEDK-49): platform administration, SUPER_ADMIN only
    PLATFORM_PLANS_MANAGE(
            "platform.plans.manage", PermissionScope.PLATFORM, "Administrar planes, módulos, límites y tarifas"),
    PLATFORM_TENANTS_MANAGE(
            "platform.tenants.manage",
            PermissionScope.PLATFORM,
            "Dar de alta y administrar clínicas, sus suscripciones y reactivaciones"),
    PLATFORM_TENANTS_PURGE(
            "platform.tenants.purge", PermissionScope.PLATFORM, "Solicitar y confirmar la baja definitiva de clínicas"),
    PLATFORM_SETTINGS_MANAGE(
            "platform.settings.manage", PermissionScope.PLATFORM, "Administrar los ajustes de la plataforma");

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

    /** Module that owns the permission: the first segment of the code, {@code identity} for {@code roles.*}. */
    public String module() {
        String first = code.substring(0, code.indexOf('.'));
        return "roles".equals(first) ? "identity" : first;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(permission -> permission.code.equals(code)).findFirst();
    }

    /** Permissions of the scope, in catalog order. */
    public static List<Permission> ofScope(PermissionScope scope) {
        return Arrays.stream(values()).filter(permission -> permission.scope == scope).toList();
    }
}
