package com.kuador.medical.identity.role;

/**
 * Protected roles every clinic receives at signup (FR-008, FR-009). They cannot be edited or
 * deleted; their scope difference is still [POR DEFINIR] in the glossary, so both hold every
 * clinic permission.
 */
public enum SystemRole {
    OWNER("Dueño de la clínica"),
    ADMIN("Administrador de la clínica");

    private final String description;

    SystemRole(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
