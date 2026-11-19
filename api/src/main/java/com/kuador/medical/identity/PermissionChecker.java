package com.kuador.medical.identity;

import java.util.UUID;

/**
 * Public permission check of identity for the other modules (Constitution II). Handlers declare
 * their permission with {@code @RequiresPermission}; this is for checks a declaration cannot
 * express, such as a permission in a branch other than the active one. Every answer is read from
 * the database for the current request (ADR-008) and only covers the caller's clinic.
 */
public interface PermissionChecker {

    /** Whether the caller of the current request holds the permission in its active context. */
    boolean hasPermission(Permission permission);

    /**
     * Whether the caller holds the permission in a branch of its clinic: its clinic-wide
     * assignment (OWNER) or its assignment in that branch. A branch of another clinic, or no
     * caller, is always {@code false}.
     */
    boolean hasPermissionInBranch(Permission permission, UUID branchId);

    /**
     * Same as {@link #hasPermission} but rejects with 401 {@code UNAUTHENTICATED} or 403
     * {@code FORBIDDEN}.
     */
    void requirePermission(Permission permission);
}
