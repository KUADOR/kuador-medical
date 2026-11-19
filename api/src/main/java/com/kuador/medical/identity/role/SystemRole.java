package com.kuador.medical.identity.role;

/**
 * Protected roles every clinic receives when it is created (plan of MEDK-12): OWNER can do
 * everything in its clinic and is immutable; ADMIN cannot be deleted and only an OWNER may edit
 * its permissions. Names and permissions come from {@code identity/role-seed.yaml}.
 */
public enum SystemRole {
    OWNER,
    ADMIN
}
