package com.kuador.medical.shared.security;

/**
 * Answers who is calling, for the access interceptor. The identity module provides the real
 * implementation (token, scope and effective permissions resolved on each request); until then
 * {@link DenyAllAccessChecker} treats every caller as anonymous.
 */
public interface AccessChecker {

    /** Whether the current request carries a valid authentication. */
    boolean isAuthenticated();

    /** Whether the current caller holds the permission; always {@code false} when not authenticated. */
    boolean hasPermission(String permission);
}
