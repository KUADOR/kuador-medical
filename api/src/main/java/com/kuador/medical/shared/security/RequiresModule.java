package com.kuador.medical.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The handler belongs to a module that the plan of the clinic must enable (ADR-004, ADR-017). A
 * clinic whose plan does not include it responds 403 {@code MODULE_NOT_IN_PLAN}. The value is a
 * code of the {@code ModuleCode} catalog of the platform (for example {@code PATIENTS}); a test
 * fails the build for an unknown code. On the method it replaces the one of the class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresModule {

    /** Code of the module, as in {@code ModuleCode}. */
    String value();
}
