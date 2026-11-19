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
}
