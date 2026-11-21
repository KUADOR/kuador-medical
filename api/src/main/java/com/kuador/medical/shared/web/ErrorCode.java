package com.kuador.medical.shared.web;

/**
 * Stable error codes of the API, mirrored from {@code ErrorCode} in the OpenAPI contract.
 * Clients map each code to a user-facing message.
 */
public enum ErrorCode {
    VALIDATION,
    EMAIL_TAKEN,
    INVALID_CREDENTIALS,
    WEAK_PASSWORD,
    UNAUTHENTICATED,
    FORBIDDEN,
    PASSWORD_CHANGE_REQUIRED,
    NOT_FOUND,
    ROLE_NAME_TAKEN,
    VERSION_CONFLICT,
    SYSTEM_ROLE_IMMUTABLE,
    ROLE_IN_USE,
    INTERNAL_ERROR,
    // MEDK-12 user registration (contracts/user-registration.openapi.yaml)
    TOO_MANY_ATTEMPTS,
    INVALID_SESSION_TOKEN,
    ONE_TIME_TOKEN_INVALID,
    BRANCH_NOT_ASSIGNED,
    PRIVILEGE_ESCALATION,
    SELF_MODIFICATION_FORBIDDEN,
    LAST_OWNER,
    PLAN_LIMIT_REACHED,
    IDEMPOTENCY_KEY_REUSED,
    RESOURCE_ID_TAKEN,
    ACCOUNT_ALREADY_ACTIVE,
    PATIENT_DUPLICATE,
    MINOR_CANNOT_HAVE_ACCOUNT,
    ADULT_AUTHORIZATION_REQUIRED,
    LAST_LEGAL_GUARDIAN,
    CONTACT_CHANNEL_UNAVAILABLE,
    // MEDK-49 platform and subscriptions (contracts/platform.openapi.yaml)
    PLAN_ARCHIVED,
    RUC_ALREADY_REGISTERED,
    INVALID_SUBSCRIPTION_TRANSITION,
    REACTIVATION_WINDOW_CLOSED,
    // MEDK-67 OWNER invitation and mandatory second factor (ADR-019)
    INVITATION_INVALID_OR_EXPIRED,
    TWO_FACTOR_REQUIRED,
    TWO_FACTOR_CODE_INVALID,
    TWO_FACTOR_ALREADY_ENABLED,
    // MEDK-69 plan changes, capabilities and read-only mode (ADR-017)
    TENANT_READ_ONLY,
    MODULE_NOT_IN_PLAN,
    PLAN_CHANGE_NOT_ALLOWED
}
