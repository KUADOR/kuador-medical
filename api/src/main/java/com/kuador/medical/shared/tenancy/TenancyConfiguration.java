package com.kuador.medical.shared.tenancy;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the tenant resolver for every entity that declares {@code @TenantId}.
 */
@Configuration(proxyBeanMethods = false)
class TenancyConfiguration {

    @Bean
    TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    HibernatePropertiesCustomizer tenantResolverCustomizer(TenantIdentifierResolver resolver) {
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
