package com.kuador.medical.shared.web;

import com.kuador.medical.shared.capability.CapabilityChecker;
import com.kuador.medical.shared.capability.CoreCapabilityChecker;
import com.kuador.medical.shared.capability.ModuleCapabilityResolver;
import com.kuador.medical.shared.security.AccessChecker;
import com.kuador.medical.shared.security.AccessControlInterceptor;
import com.kuador.medical.shared.security.DenyAllAccessChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API base path, CORS and the deny-by-default access interceptor.
 */
@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

    static final String API_PREFIX = "/api/v1";

    private final ObjectProvider<AccessChecker> accessChecker;
    private final ObjectProvider<CapabilityChecker> capabilityChecker;
    private final CorsProperties cors;

    WebConfiguration(
            ObjectProvider<AccessChecker> accessChecker,
            ObjectProvider<CapabilityChecker> capabilityChecker,
            CorsProperties cors) {
        this.accessChecker = accessChecker;
        this.capabilityChecker = capabilityChecker;
        this.cors = cors;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage("com.kuador.medical"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var interceptor = new AccessControlInterceptor(
                accessChecker.getIfAvailable(DenyAllAccessChecker::new),
                capabilityChecker.getIfAvailable(CoreCapabilityChecker::new),
                new ModuleCapabilityResolver());
        // Every handler is checked; only the servlet error page (rendered after a rejection) is exempt.
        registry.addInterceptor(interceptor).addPathPatterns("/**").excludePathPatterns("/error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!cors.allowedOrigins().isEmpty()) {
            registry.addMapping(API_PREFIX + "/**")
                    .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("Authorization", "Content-Type")
                    .maxAge(3600);
        }
    }
}
