package com.kuador.medical.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

/**
 * The "prod" profile (staging and production) refuses to start when a variable or secret it needs
 * is missing or blank (MEDK-71). The error names the variables and never shows a value.
 */
class ProdConfigFailFastTest {

    private static final String SECRET_VALUE = "do-not-print-" + UUID.randomUUID();

    private final ProdEnvironmentValidator validator = new ProdEnvironmentValidator();

    @Test
    void requiresDatabaseSecretsEmailAndLinks() {
        assertThat(ProdEnvironmentValidator.REQUIRED_VARIABLES).containsExactly(
                "DB_URL", "DB_USERNAME", "DB_PASSWORD", "KUADOR_JWT_SECRET", "KUADOR_TOTP_ENCRYPTION_KEY",
                "KUADOR_SMTP_HOST", "KUADOR_MAIL_FROM", "KUADOR_WEB_BASE_URL");
    }

    @Test
    void prodProfileWithEveryVariableStarts() {
        validator.postProcessEnvironment(prodEnvironment(), new SpringApplication());
    }

    @Test
    void missingAndBlankVariablesAreNamedWithoutShowingAnyValue() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("KUADOR_TOTP_ENCRYPTION_KEY", " ");
        environment.setProperty("KUADOR_MAIL_FROM", "");

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOfSatisfying(MissingEnvironmentVariablesException.class, e -> {
                    assertThat(e.profile()).isEqualTo("prod");
                    assertThat(e.variables()).containsExactly("KUADOR_TOTP_ENCRYPTION_KEY", "KUADOR_MAIL_FROM");
                    assertThat(e.getMessage())
                            .contains("prod", "KUADOR_TOTP_ENCRYPTION_KEY", "KUADOR_MAIL_FROM")
                            .doesNotContain(SECRET_VALUE, "KUADOR_JWT_SECRET");
                });
    }

    @Test
    void smtpWithAuthenticationNeedsUsernameAndPassword() {
        MockEnvironment environment = prodEnvironment();
        environment.setProperty("KUADOR_SMTP_USERNAME", "");
        environment.setProperty("KUADOR_SMTP_PASSWORD", "");

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOfSatisfying(MissingEnvironmentVariablesException.class, e -> assertThat(e.variables())
                        .containsExactly("KUADOR_SMTP_USERNAME", "KUADOR_SMTP_PASSWORD"));
    }

    @Test
    void smtpAuthenticationIsOnUnlessExplicitlyDisabled() {
        MockEnvironment withoutAuthFlag = prodEnvironment();
        withoutAuthFlag.setProperty("KUADOR_SMTP_PASSWORD", "");
        assertThatThrownBy(() -> validator.postProcessEnvironment(withoutAuthFlag, new SpringApplication()))
                .isInstanceOf(MissingEnvironmentVariablesException.class);

        MockEnvironment relayWithoutAuth = prodEnvironment();
        relayWithoutAuth.setProperty("KUADOR_SMTP_AUTH", "false");
        relayWithoutAuth.setProperty("KUADOR_SMTP_USERNAME", "");
        relayWithoutAuth.setProperty("KUADOR_SMTP_PASSWORD", "");
        validator.postProcessEnvironment(relayWithoutAuth, new SpringApplication());
    }

    @Test
    void otherProfilesAreNotChecked() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        validator.postProcessEnvironment(environment, new SpringApplication());
    }

    @Test
    void failureAnalysisPointsToTheSecretManagerInProd() {
        var exception = new MissingEnvironmentVariablesException("prod", List.of("KUADOR_SMTP_HOST"));

        FailureAnalysis analysis = new MissingEnvironmentVariablesFailureAnalyzer().analyze(exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("prod", "KUADOR_SMTP_HOST");
        assertThat(analysis.getAction()).contains("secret manager").doesNotContain(".env.example");
    }

    private static MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("DB_URL", "jdbc:postgresql://db.example.test:5432/medical");
        environment.setProperty("DB_USERNAME", "medical");
        environment.setProperty("DB_PASSWORD", SECRET_VALUE);
        environment.setProperty("KUADOR_JWT_SECRET", SECRET_VALUE + SECRET_VALUE);
        environment.setProperty("KUADOR_TOTP_ENCRYPTION_KEY", SECRET_VALUE + SECRET_VALUE);
        environment.setProperty("KUADOR_SMTP_HOST", "smtp.example.test");
        environment.setProperty("KUADOR_SMTP_USERNAME", "mailer");
        environment.setProperty("KUADOR_SMTP_PASSWORD", SECRET_VALUE);
        environment.setProperty("KUADOR_MAIL_FROM", "no-reply@medical.example.test");
        environment.setProperty("KUADOR_WEB_BASE_URL", "https://staging.example.test");
        return environment;
    }
}
