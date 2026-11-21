package com.kuador.medical.platform.access;

import com.kuador.medical.platform.api.PlanCapabilities;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link TenantWriteGuard} after the access control interceptor of the shared module, so
 * that an anonymous or unauthorized request keeps its 401 or 403 and the plan is checked only for
 * callers that may run the handler.
 */
@Configuration(proxyBeanMethods = false)
class TenantAccessConfiguration implements WebMvcConfigurer {

    private final PlanCapabilities capabilities;

    TenantAccessConfiguration(PlanCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantWriteGuard(capabilities))
                .addPathPatterns("/**")
                .excludePathPatterns("/error")
                .order(Ordered.LOWEST_PRECEDENCE);
    }
}
