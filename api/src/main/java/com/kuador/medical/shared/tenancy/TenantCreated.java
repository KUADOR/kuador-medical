package com.kuador.medical.shared.tenancy;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Published by the platform inside the transaction that creates a tenant, with the new tenant
 * bound in {@link TenantContext}. Listeners prepare their tenant data (for example identity seeds
 * the clinic roles and invites the initial OWNER) in that same transaction, so a failure leaves
 * nothing behind. It lives in the shared module so that identity can react to it without
 * depending on the platform module.
 *
 * @param initialOwner the OWNER to invite (clinic onboarded by the SUPER_ADMIN, MEDK-67);
 *                     {@code null} when the owner is created otherwise (self-signup)
 */
public record TenantCreated(UUID tenantId, @Nullable InitialOwner initialOwner) {

    public TenantCreated {
        Objects.requireNonNull(tenantId, "tenantId");
    }

    public TenantCreated(UUID tenantId) {
        this(tenantId, null);
    }

    /** Person designated as OWNER at onboarding: invited by email (plan D7, FR-008). */
    public record InitialOwner(String fullName, String email) {

        public InitialOwner {
            Objects.requireNonNull(fullName, "fullName");
            Objects.requireNonNull(email, "email");
        }

        @Override
        public String toString() {
            return "InitialOwner[redacted]";
        }
    }
}
