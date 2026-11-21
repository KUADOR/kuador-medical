package com.kuador.medical.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a writing handler (POST, PUT, PATCH or DELETE) answer while the clinic is read-only
 * (ADR-017: SUSPENDED, or CANCELLED during retention). Every other write of a clinic responds 403
 * {@code TENANT_READ_ONLY}. Only operations that keep the clinic able to sign in, read and export
 * its data use it: switching branch, changing the password, the second factor and the export.
 * Public handlers are never blocked: they are not bound to a clinic session.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowedWhenReadOnly {}
