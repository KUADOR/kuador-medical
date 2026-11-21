package com.kuador.medical.audit;

/** Audited security actions; mirrors the {@code audit_event_action_check} constraint. */
public enum AuditAction {
    TENANT_SIGNED_UP,
    LOGIN_SUCCEEDED,
    PASSWORD_CHANGED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    ROLES_SEEDED,
    ROLE_ASSIGNMENTS_REPLACED,
    LOGIN_FAILED,
    LOGIN_LOCKED,
    LOGOUT,
    BRANCH_SWITCHED,
    REFRESH_TOKEN_REUSED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    STAFF_USER_CREATED,
    STAFF_USER_UPDATED,
    STAFF_USER_DISABLED,
    STAFF_USER_ENABLED,
    INVITATION_SENT,
    INVITATION_ACCEPTED,
    ACCOUNT_PROVISIONED,
    // MEDK-17 patients: reads are audited too (Constitution V)
    PATIENTS_LISTED,
    PATIENT_VIEWED,
    PATIENT_REGISTERED,
    PATIENT_UPDATED,
    PATIENT_SELF_REGISTERED,
    PATIENT_CONSENT_RECORDED,
    GUARDIAN_LINK_CREATED,
    GUARDIAN_LINK_ENDED,
    PATIENT_ACCOUNT_INVITED,
    PATIENT_REGISTRATION_SETTINGS_UPDATED,
    EMAIL_VERIFICATION_SENT,
    EMAIL_VERIFIED,
    // MEDK-18 transition to adulthood and links authorized by the adult patient
    PATIENT_BECAME_ADULT,
    /** The new adult has no usable contact channel: the staff invites it later. */
    ADULTHOOD_INVITATION_PENDING,
    GUARDIAN_LINK_AUTHORIZED,
    // MEDK-65 platform plans, prices and settings (platform-level events, no tenant)
    PLAN_CREATED,
    PLAN_UPDATED,
    PLAN_ARCHIVED,
    PLAN_PRICE_CREATED,
    PLATFORM_SETTINGS_UPDATED,
    // MEDK-66 clinic onboarding and administration by the SUPER_ADMIN (event of the clinic)
    TENANT_ONBOARDED,
    TENANT_UPDATED,
    // MEDK-67 OWNER invitation, terms acceptance and second factor (ADR-019)
    OWNER_INVITATION_SENT,
    OWNER_INVITATION_ACCEPTED,
    TERMS_ACCEPTED,
    TWO_FACTOR_ENABLED,
    /** Reset by a SUPER_ADMIN with a reason (plan D7). */
    TWO_FACTOR_RESET,
    /** Too many wrong codes: the second factor is locked for a while. */
    TWO_FACTOR_LOCKED,
    // MEDK-68 subscription lifecycle (event of the clinic; the actor is null for events and the job)
    SUBSCRIPTION_STATUS_CHANGED,
    SUBSCRIPTION_RENEWED,
    SUBSCRIPTION_PRICE_LOCK_UPDATED,
    /** A PaymentFailed event: recorded, the status does not change (plan D5). */
    SUBSCRIPTION_PAYMENT_FAILED,
    /** A PaymentReceived event that the state machine rejects, for example on a cancelled clinic (D12). */
    SUBSCRIPTION_PAYMENT_REJECTED,
    PRICE_CHANGE_NOTICE_SENT
}
