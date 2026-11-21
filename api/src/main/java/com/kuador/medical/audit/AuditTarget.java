package com.kuador.medical.audit;

import java.util.Objects;
import java.util.UUID;

/** Target of an audited action: its type (for example {@code TENANT}, {@code ACCOUNT}) and id. */
public record AuditTarget(String type, UUID id) {

    public AuditTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static AuditTarget tenant(UUID id) {
        return new AuditTarget("TENANT", id);
    }

    public static AuditTarget account(UUID id) {
        return new AuditTarget("ACCOUNT", id);
    }

    public static AuditTarget role(UUID id) {
        return new AuditTarget("ROLE", id);
    }

    public static AuditTarget patient(UUID id) {
        return new AuditTarget("PATIENT", id);
    }

    public static AuditTarget plan(UUID id) {
        return new AuditTarget("PLAN", id);
    }

    public static AuditTarget planPrice(UUID id) {
        return new AuditTarget("PLAN_PRICE", id);
    }

    public static AuditTarget subscription(UUID id) {
        return new AuditTarget("SUBSCRIPTION", id);
    }

    public static AuditTarget tenantExport(UUID id) {
        return new AuditTarget("TENANT_EXPORT", id);
    }

    public static AuditTarget purgeRequest(UUID id) {
        return new AuditTarget("PURGE_REQUEST", id);
    }
}
