package com.kuador.medical.identity;

/**
 * Status of a clinic account: {@code INVITED} until the invitation is accepted (no password yet),
 * {@code PENDING_EMAIL_VERIFICATION} for a self-registered patient, then {@code ACTIVE}, and
 * {@code DISABLED} when access is withdrawn (data and history are kept).
 */
public enum ClinicAccountStatus {
    INVITED,
    PENDING_EMAIL_VERIFICATION,
    ACTIVE,
    DISABLED
}
