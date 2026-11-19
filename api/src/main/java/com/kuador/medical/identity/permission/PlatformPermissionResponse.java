package com.kuador.medical.identity.permission;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;

/** {@code PermissionInfo} of {@code identity.openapi.yaml}, served by {@code /platform/permissions}. */
record PlatformPermissionResponse(String code, PermissionScope scope, String description) {

    static PlatformPermissionResponse of(Permission permission) {
        return new PlatformPermissionResponse(permission.code(), permission.scope(), permission.description());
    }
}
