package com.kuador.medical.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates clinic accounts for the other modules (plan of MEDK-12): the owner of a clinic being
 * signed up, and invited accounts of patients and guardians. Staff users are registered through
 * {@code /staff-users}. Every method works on the clinic bound in the current request.
 */
public interface AccountProvisioning {

    /**
     * Creates the active owner of a clinic that is being signed up: the account and its
     * clinic-wide OWNER assignment (FR-009). Must run inside the transaction that creates the
     * tenant, with {@code tenantId} bound as the current tenant, after the roles are seeded on
     * {@link com.kuador.medical.shared.tenancy.TenantCreated}.
     *
     * @return id of the owner account
     */
    UUID bootstrapOwner(UUID tenantId, NewTenantOwner owner);

    /**
     * Creates an {@code INVITED} account of a patient or a guardian and emails it a one-time
     * invitation (ADR-014). Joins the transaction of the caller; the email leaves after commit.
     *
     * @return id of the new account
     * @throws com.kuador.medical.shared.web.ApiException {@code EMAIL_TAKEN} when the email is used in the clinic
     * @throws IllegalArgumentException for a {@code STAFF} account
     */
    UUID inviteAccount(NewClinicAccount account);

    /**
     * Invalidates the pending invitations of an invited account and emails a new one.
     *
     * @throws com.kuador.medical.shared.web.ApiException {@code NOT_FOUND} outside the clinic,
     *         {@code ACCOUNT_ALREADY_ACTIVE} when the account is not invited
     */
    void resendInvitation(UUID accountId);

    /** The account in the current clinic; empty for an unknown id or an account of another clinic. */
    Optional<ClinicAccountInfo> find(UUID accountId);
}
