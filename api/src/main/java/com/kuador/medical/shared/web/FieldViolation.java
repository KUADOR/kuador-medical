package com.kuador.medical.shared.web;

import java.util.Objects;

/**
 * Field-level validation error of a Problem Details response ({@code FieldError} in the contract).
 *
 * @param field JSON path of the invalid field, for example {@code permissions[0]}
 * @param code  stable validation code, for example {@code UNKNOWN_PERMISSION}
 */
public record FieldViolation(String field, String code) {

    public FieldViolation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
    }
}
