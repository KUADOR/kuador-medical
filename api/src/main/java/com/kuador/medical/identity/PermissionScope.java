package com.kuador.medical.identity;

/** {@code TENANT} permissions are assignable to clinic roles; {@code PLATFORM} permissions never are. */
public enum PermissionScope {
    TENANT,
    PLATFORM
}
