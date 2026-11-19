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
    ROLE_ASSIGNMENTS_REPLACED
}
