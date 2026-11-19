package com.kuador.medical.shared.capability;

/**
 * Capabilities a plan can enable (ADR-004). The skeleton only knows the core ones, which are
 * part of every plan; business modules add theirs when they are built.
 */
public enum Capability {
    /** Platform operations: system status, tenants, signup, audit. */
    PLATFORM,
    /** Accounts, authentication, permissions and roles. */
    IDENTITY,
    /** Administrative registration of patients, guardians, consent and self-registration (MEDK-17). */
    PATIENTS
}
