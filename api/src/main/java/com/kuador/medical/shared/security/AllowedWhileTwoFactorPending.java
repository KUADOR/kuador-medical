package com.kuador.medical.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets an authenticated handler answer while the session has not passed the second factor yet
 * (ADR-019). Every other non-public handler responds 401 {@code TWO_FACTOR_REQUIRED}. Only the
 * {@code /auth/two-factor/**} operations use it; logout is public.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowedWhileTwoFactorPending {}
