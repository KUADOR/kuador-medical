package com.kuador.medical.platform.access;

import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.api.PlanCapabilities;
import com.kuador.medical.platform.plan.ModuleCode;
import com.kuador.medical.shared.security.AllowedWhenReadOnly;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.security.RequiresModule;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Central check of the plan of the clinic on every request of a clinic session (ADR-017), after
 * the access control interceptor (ADR-008):
 *
 * <ul>
 *   <li>a purged clinic ({@code NONE}) has no access: 403 {@code FORBIDDEN};
 *   <li>a handler marked {@link RequiresModule} needs the module in the plan: 403
 *       {@code MODULE_NOT_IN_PLAN};
 *   <li>while the clinic is read-only ({@code READ_ONLY} or {@code RETENTION}) every write (POST,
 *       PUT, PATCH, DELETE) responds 403 {@code TENANT_READ_ONLY}, except the handlers marked
 *       {@link AllowedWhenReadOnly}. Nothing is deleted: reads keep answering.
 * </ul>
 *
 * Platform sessions (SUPER_ADMIN) and public handlers have no clinic session and are not checked.
 */
class TenantWriteGuard implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final PlanCapabilities capabilities;

    TenantWriteGuard(PlanCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || isMarked(method, PublicEndpoint.class)) {
            return true;
        }
        Optional<UUID> tenant = TenantContext.current();
        if (tenant.isEmpty()) {
            return true;
        }
        AccessMode mode = capabilities.accessMode(tenant.get());
        if (mode == AccessMode.NONE) {
            throw ApiException.forbidden("The clinic no longer exists.");
        }
        RequiresModule module = requiredModule(method);
        if (module != null && !capabilities.isModuleEnabled(tenant.get(), ModuleCode.valueOf(module.value()))) {
            throw ApiException.moduleNotInPlan(module.value());
        }
        if (WRITE_METHODS.contains(request.getMethod()) && !mode.allowsWrites()
                && !isMarked(method, AllowedWhenReadOnly.class)) {
            throw ApiException.tenantReadOnly();
        }
        return true;
    }

    private static @Nullable RequiresModule requiredModule(HandlerMethod method) {
        RequiresModule onMethod = method.getMethodAnnotation(RequiresModule.class);
        return onMethod != null ? onMethod : method.getBeanType().getAnnotation(RequiresModule.class);
    }

    private static boolean isMarked(HandlerMethod method, Class<? extends Annotation> marker) {
        return method.hasMethodAnnotation(marker) || method.getBeanType().isAnnotationPresent(marker);
    }
}
