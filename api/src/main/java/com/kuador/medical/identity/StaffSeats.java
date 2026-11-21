package com.kuador.medical.identity;

/**
 * Staff accounts of the current clinic that take a seat of the plan (invited, pending email
 * verification or active), the usage behind the {@code MAX_USERS} limit (MEDK-69, plan D11). The
 * platform reads it to report usage and to preview a downgrade; it never needs the accounts.
 */
public interface StaffSeats {

    /** Seated staff accounts of the clinic bound in the tenant context (ADR-007). */
    long seatedStaff();
}
