package com.kuador.medical.identity.permission;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.shared.security.RequiresPermission;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The fixed permission catalog (FR-012), in catalog order. */
@RestController
class PermissionController {

    /** Permissions a clinic may assign to its roles; platform permissions are never listed. */
    @RequiresPermission("roles.read")
    @GetMapping("/permissions")
    List<PermissionResponse> assignable() {
        return Permission.ofScope(PermissionScope.TENANT).stream().map(PermissionResponse::of).toList();
    }

    /** The whole catalog, for the SUPER_ADMIN (PLATFORM token only). */
    @RequiresPermission("platform.permissions.read")
    @GetMapping("/platform/permissions")
    List<PermissionResponse> all() {
        return Arrays.stream(Permission.values()).map(PermissionResponse::of).toList();
    }
}
