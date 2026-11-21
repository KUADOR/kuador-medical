package com.kuador.medical.platform.plan;

/**
 * Fixed catalog of limits a plan can set ({@code LimitCode} in {@code platform.openapi.yaml}).
 * Limits count tenant-wide totals (plan D11). Codes are stable: they are stored in {@code plan_limit}.
 */
public enum LimitCode {
    MAX_BRANCHES("Sedes", Unit.COUNT),
    MAX_USERS("Usuarios", Unit.COUNT),
    MAX_PATIENTS("Pacientes", Unit.COUNT),
    MAX_APPOINTMENTS("Citas", Unit.COUNT),
    MAX_STORAGE_MB("Almacenamiento", Unit.MEGABYTES);

    /** Unit of the limit value. */
    public enum Unit {
        COUNT,
        MEGABYTES
    }

    private final String label;
    private final Unit unit;

    LimitCode(String label, Unit unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public Unit unit() {
        return unit;
    }
}
