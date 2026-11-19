package com.kuador.medical.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets an authenticated handler answer while the caller still has to change its password
 * ({@code mustChangePassword}). Every other non-public handler responds 403
 * {@code PASSWORD_CHANGE_REQUIRED}. Only {@code POST /auth/password} and {@code GET /me} use it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowedWhilePasswordChangeRequired {}
