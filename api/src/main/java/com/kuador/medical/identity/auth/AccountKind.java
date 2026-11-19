package com.kuador.medical.identity.auth;

/** Kind of account inside a clinic (contract {@code AccountKind}); patients and guardians arrive with MEDK-17. */
enum AccountKind {
    STAFF,
    PATIENT,
    GUARDIAN
}
