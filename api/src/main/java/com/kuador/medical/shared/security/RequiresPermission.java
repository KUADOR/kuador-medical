package com.kuador.medical.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller to hold a permission of the fixed catalog, for example {@code roles.read}.
 * Matches {@code x-required-permission: <code>} in the contract (ADR-008). A method-level
 * declaration replaces the one of its controller.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    /** Permission code of the catalog. */
    String value();
}
