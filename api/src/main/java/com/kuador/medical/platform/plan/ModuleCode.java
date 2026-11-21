package com.kuador.medical.platform.plan;

/**
 * Fixed catalog of modules a plan can enable ({@code ModuleCode} in {@code platform.openapi.yaml}).
 * Codes are stable: they are stored in {@code plan_module}. Labels are shown to people.
 */
public enum ModuleCode {
    AGENDA("Agenda y citas"),
    PATIENTS("Pacientes"),
    CLINICAL_RECORDS("Historia clínica"),
    CONSULTATION("Consulta"),
    ODONTOGRAM("Odontograma"),
    USERS("Usuarios y roles"),
    PHARMACY("Farmacia"),
    WAREHOUSE("Bodega"),
    CASH_REGISTER("Caja"),
    COLLECTIONS("Cobranzas"),
    NOTIFICATIONS("Notificaciones"),
    E_INVOICING("Facturación electrónica"),
    PUBLIC_API("API pública"),
    ADVANCED_REPORTS("Reportes avanzados");

    private final String label;

    ModuleCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
