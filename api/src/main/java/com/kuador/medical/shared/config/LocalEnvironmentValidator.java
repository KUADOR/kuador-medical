package com.kuador.medical.shared.config;

import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Fail fast in the "local" profile (MEDK-26 plan, decision 2): before any bean is created, every
 * required environment variable must be present and not blank. The error names the missing
 * variables and never shows a value. Runs after the config data (profiles and {@code .env} import)
 * is loaded. Registered in {@code META-INF/spring.factories}.
 */
public class LocalEnvironmentValidator implements EnvironmentPostProcessor, Ordered {

    static final String LOCAL_PROFILE = "local";

    /** Variables the local profile reads without a default. Names only: values are never logged. */
    static final List<String> REQUIRED_VARIABLES =
            List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "KUADOR_JWT_SECRET", "KUADOR_TOTP_ENCRYPTION_KEY",
                    "LOCAL_SEED_PASSWORD");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(LOCAL_PROFILE))) {
            return;
        }
        List<String> missing = REQUIRED_VARIABLES.stream()
                .filter(variable -> isBlank(environment.getProperty(variable)))
                .toList();
        if (!missing.isEmpty()) {
            throw new MissingEnvironmentVariablesException(missing);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
