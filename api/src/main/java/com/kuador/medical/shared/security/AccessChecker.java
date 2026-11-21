package com.kuador.medical.shared.security;

import java.util.Optional;

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

    /**
     * Whether the authenticated caller must change its password before operating. While it is
     * {@code true}, only handlers marked {@link AllowedWhilePasswordChangeRequired} answer.
     */
    default boolean isPasswordChangeRequired() {
        return false;
    }

    /**
     * What the authenticated caller must do before operating when its session has not passed the
     * second factor (ADR-019): {@code ENROLLMENT_REQUIRED} or {@code VERIFICATION_REQUIRED}. While
     * present, only handlers marked {@link AllowedWhileTwoFactorPending} answer.
     */
    default Optional<String> pendingTwoFactorStep() {
        return Optional.empty();
    }
}
