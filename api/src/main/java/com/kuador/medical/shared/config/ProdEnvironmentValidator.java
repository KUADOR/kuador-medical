package com.kuador.medical.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Fail fast in the "prod" profile (staging and production, MEDK-71): before any bean is created,
 * every variable and secret the environment must provide is present and not blank. Without this,
 * a missing SMTP host or web URL would only show up when the first invitation email fails. The
 * error names the missing variables and never shows a value. Registered in
 * {@code META-INF/spring.factories}.
 */
public class ProdEnvironmentValidator implements EnvironmentPostProcessor, Ordered {

    static final String PROD_PROFILE = "prod";

    /** Variables staging and production must define. Names only: values are never logged. */
    static final List<String> REQUIRED_VARIABLES = List.of(
            "DB_URL", "DB_USERNAME", "DB_PASSWORD", "KUADOR_JWT_SECRET", "KUADOR_TOTP_ENCRYPTION_KEY",
            "KUADOR_SMTP_HOST", "KUADOR_MAIL_FROM", "KUADOR_WEB_BASE_URL");

    /** Required too unless KUADOR_SMTP_AUTH is "false" (SMTP authentication is on by default). */
    static final List<String> SMTP_AUTH_VARIABLES = List.of("KUADOR_SMTP_USERNAME", "KUADOR_SMTP_PASSWORD");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }
        List<String> required = new ArrayList<>(REQUIRED_VARIABLES);
        if (!"false".equalsIgnoreCase(trim(environment.getProperty("KUADOR_SMTP_AUTH")))) {
            required.addAll(SMTP_AUTH_VARIABLES);
        }
        List<String> missing = required.stream()
                .filter(variable -> isBlank(environment.getProperty(variable)))
                .toList();
        if (!missing.isEmpty()) {
            throw new MissingEnvironmentVariablesException(PROD_PROFILE, missing);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
