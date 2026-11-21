package com.kuador.medical.shared.security;

import com.kuador.medical.shared.capability.Capability;
import com.kuador.medical.shared.capability.CapabilityChecker;
import com.kuador.medical.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deny by default (ADR-008): a request only reaches a handler that declares exactly one access
 * level ({@link PublicEndpoint}, {@link AuthenticatedEndpoint} or {@link RequiresPermission}),
 * whose capability is enabled (ADR-004) and whose requirement the caller meets. An authenticated
 * caller that must change its password only reaches handlers marked
 * {@link AllowedWhilePasswordChangeRequired}, and one whose session has not passed the second
 * factor only those marked {@link AllowedWhileTwoFactorPending} (ADR-019); public handlers ignore
 * the caller.
 */
public class AccessControlInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessControlInterceptor.class);

    private final AccessChecker accessChecker;
    private final CapabilityChecker capabilityChecker;
    private final Function<Class<?>, Optional<Capability>> capabilityResolver;

    public AccessControlInterceptor(
            AccessChecker accessChecker,
            CapabilityChecker capabilityChecker,
            Function<Class<?>, Optional<Capability>> capabilityResolver) {
        this.accessChecker = accessChecker;
        this.capabilityChecker = capabilityChecker;
        this.capabilityResolver = capabilityResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            throw ApiException.forbidden("Access denied.");
        }
        AccessRule rule = accessRuleOf(method);
        if (rule instanceof AccessRule.Undeclared) {
            log.warn("Denied undeclared handler {}#{}", method.getBeanType().getName(), method.getMethod().getName());
            throw ApiException.forbidden("Access denied.");
        }
        Optional<Capability> capability = capabilityResolver.apply(method.getBeanType());
        if (capability.isEmpty() || !capabilityChecker.isEnabled(capability.get())) {
            throw ApiException.forbidden("The capability is not enabled.");
        }
        switch (rule) {
            case AccessRule.Public p -> {}
            case AccessRule.Authenticated a -> requireAuthentication(method);
            case AccessRule.Permission(String permission) -> {
                requireAuthentication(method);
                if (!accessChecker.hasPermission(permission)) {
                    throw ApiException.forbidden("Access denied.");
                }
            }
            case AccessRule.Undeclared u -> throw ApiException.forbidden("Access denied.");
        }
        return true;
    }

    private void requireAuthentication(HandlerMethod method) {
        if (!accessChecker.isAuthenticated()) {
            throw ApiException.unauthenticated();
        }
        Optional<String> twoFactorStep = accessChecker.pendingTwoFactorStep();
        if (twoFactorStep.isPresent() && !isMarked(method, AllowedWhileTwoFactorPending.class)) {
            throw ApiException.twoFactorRequired(twoFactorStep.get());
        }
        if (accessChecker.isPasswordChangeRequired() && !allowedWhilePasswordChangeRequired(method)) {
            throw ApiException.passwordChangeRequired();
        }
    }

    private static boolean allowedWhilePasswordChangeRequired(HandlerMethod method) {
        return isMarked(method, AllowedWhilePasswordChangeRequired.class);
    }

    private static boolean isMarked(HandlerMethod method, Class<? extends java.lang.annotation.Annotation> marker) {
        return method.hasMethodAnnotation(marker) || method.getBeanType().isAnnotationPresent(marker);
    }

    private static AccessRule accessRuleOf(HandlerMethod method) {
        AccessRule onMethod = declaredOn(method.getMethod());
        if (!(onMethod instanceof AccessRule.Undeclared undeclared) || undeclared.ambiguous()) {
            return onMethod;
        }
        return declaredOn(method.getBeanType());
    }

    private static AccessRule declaredOn(AnnotatedElement element) {
        PublicEndpoint publicEndpoint = element.getAnnotation(PublicEndpoint.class);
        AuthenticatedEndpoint authenticated = element.getAnnotation(AuthenticatedEndpoint.class);
        RequiresPermission permission = element.getAnnotation(RequiresPermission.class);
        int declarations = (publicEndpoint != null ? 1 : 0) + (authenticated != null ? 1 : 0) + (permission != null ? 1 : 0);
        if (declarations != 1) {
            return new AccessRule.Undeclared(declarations > 1);
        }
        if (publicEndpoint != null) {
            return new AccessRule.Public();
        }
        if (authenticated != null) {
            return new AccessRule.Authenticated();
        }
        return new AccessRule.Permission(permission.value());
    }

    private sealed interface AccessRule {
        record Public() implements AccessRule {}

        record Authenticated() implements AccessRule {}

        record Permission(String code) implements AccessRule {}

        /** No declaration, or more than one ({@code ambiguous}): both are denied. */
        record Undeclared(boolean ambiguous) implements AccessRule {}
    }
}
