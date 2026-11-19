package com.kuador.medical.patients.registration;

/** {@code LEGAL_GUARDIAN} gives access while the patient is a minor; the others are authorized by an adult patient. */
enum LinkType {
    LEGAL_GUARDIAN,
    EMERGENCY_CONTACT,
    DELEGATED_ACCESS
}
