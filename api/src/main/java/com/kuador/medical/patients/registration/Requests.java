package com.kuador.medical.patients.registration;

/**
 * Patterns of the contract shared by the request bodies. Document types and sex codes follow a
 * versioned catalog that is [POR DEFINIR] in the glossary; until then only their format is checked.
 */
final class Requests {

    static final String DOCUMENT_TYPE = "^[A-Z][A-Z_]{1,29}$";
    static final String SEX = "^[A-Z][A-Z_]{0,29}$";
    static final String PHONE = "^\\+[1-9][0-9]{6,14}$";

    private Requests() {}
}
