package com.kuador.medical.identity.permission;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;

/** {@code PermissionInfo} of the contract. */
record PermissionResponse(String code, PermissionScope scope, String description) {

    static PermissionResponse of(Permission permission) {
        return new PermissionResponse(permission.code(), permission.scope(), permission.description());
    }
}
