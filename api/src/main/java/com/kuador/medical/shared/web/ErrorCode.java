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
    INTERNAL_ERROR
}
