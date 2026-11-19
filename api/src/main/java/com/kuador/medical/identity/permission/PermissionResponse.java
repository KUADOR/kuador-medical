package com.kuador.medical.identity.permission;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;

/** {@code PermissionInfo} of the contract. */
record PermissionResponse(String code, String module, PermissionScope scope, String description) {

    static PermissionResponse of(Permission permission) {
        return new PermissionResponse(permission.code(), permission.module(), permission.scope(), permission.description());
    }
}
