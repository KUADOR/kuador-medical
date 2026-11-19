package com.kuador.medical.shared.security;

/**
 * Fallback used while no module provides an {@link AccessChecker}: nobody is authenticated
 * and nobody holds any permission, so only public endpoints answer.
 */
public final class DenyAllAccessChecker implements AccessChecker {

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public boolean hasPermission(String permission) {
        return false;
    }
}
