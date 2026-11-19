package com.kuador.medical.shared.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the API from a browser ({@code KUADOR_CORS_ALLOWED_ORIGINS}).
 * Empty means no cross-origin access.
 */
@ConfigurationProperties("kuador.cors")
record CorsProperties(List<String> allowedOrigins) {

    CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
