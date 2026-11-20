package com.kuador.medical.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.MedicalApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

/**
 * The "local" profile refuses to start when a required environment variable is missing or blank
 * (MEDK-26 plan, decision 2). The error names the variables and never shows a value.
 */
class LocalConfigFailFastTest {

    private static final String SECRET_VALUE = "do-not-print-" + UUID.randomUUID();

    private final LocalEnvironmentValidator validator = new LocalEnvironmentValidator();

    @Test
    void requiresTheDatabaseTheSigningKeyAndTheSeedPassword() {
        assertThat(LocalEnvironmentValidator.REQUIRED_VARIABLES).containsExactly(
                "DB_URL", "DB_USERNAME", "DB_PASSWORD", "KUADOR_JWT_SECRET", "LOCAL_SEED_PASSWORD");
    }

    @Test
    void localProfileWithEveryVariableStarts() {
        MockEnvironment environment = localEnvironment();

        validator.postProcessEnvironment(environment, new SpringApplication());
    }

    @Test
    void missingAndBlankVariablesAreNamedWithoutShowingAnyValue() {
        MockEnvironment environment = localEnvironment();
        environment.setProperty("LOCAL_SEED_PASSWORD", "   ");
        MockEnvironment withoutPassword = withoutProperty(environment, "DB_PASSWORD");

        assertThatThrownBy(() -> validator.postProcessEnvironment(withoutPassword, new SpringApplication()))
                .isInstanceOfSatisfying(MissingEnvironmentVariablesException.class, e -> {
                    assertThat(e.variables()).containsExactly("DB_PASSWORD", "LOCAL_SEED_PASSWORD");
                    assertThat(e.getMessage())
                            .contains("DB_PASSWORD", "LOCAL_SEED_PASSWORD")
                            .doesNotContain("KUADOR_JWT_SECRET", "DB_URL", SECRET_VALUE);
                });
    }

    @Test
    void otherProfilesAreNotChecked() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        validator.postProcessEnvironment(environment, new SpringApplication());
    }

    @Test
    void failureAnalysisNamesTheVariablesAndTheTemplate() {
        var exception = new MissingEnvironmentVariablesException(java.util.List.of("KUADOR_JWT_SECRET"));

        FailureAnalysis analysis = new MissingEnvironmentVariablesFailureAnalyzer().analyze(exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("KUADOR_JWT_SECRET");
        assertThat(analysis.getAction()).contains(".env.example");
    }

    @Test
    void applicationDoesNotStartInTheLocalProfileWithoutItsVariables() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(MedicalApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("local");

        // Command line arguments outrank the developer's own environment, so the test is repeatable.
        assertThatThrownBy(() -> application.run(
                        "--DB_URL=jdbc:postgresql://localhost:1/unreachable",
                        "--DB_USERNAME=someone",
                        "--DB_PASSWORD=",
                        "--KUADOR_JWT_SECRET=" + SECRET_VALUE + SECRET_VALUE,
                        "--LOCAL_SEED_PASSWORD="))
                .isInstanceOfSatisfying(MissingEnvironmentVariablesException.class, e -> assertThat(e.getMessage())
                        .contains("DB_PASSWORD", "LOCAL_SEED_PASSWORD")
                        .doesNotContain(SECRET_VALUE, "someone", "unreachable"));
    }

    private static MockEnvironment localEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("DB_URL", "jdbc:postgresql://localhost:5432/medical");
        environment.setProperty("DB_USERNAME", "medical");
        environment.setProperty("DB_PASSWORD", SECRET_VALUE);
        environment.setProperty("KUADOR_JWT_SECRET", SECRET_VALUE + SECRET_VALUE);
        environment.setProperty("LOCAL_SEED_PASSWORD", SECRET_VALUE);
        return environment;
    }

    private static MockEnvironment withoutProperty(MockEnvironment source, String name) {
        MockEnvironment copy = new MockEnvironment();
        copy.setActiveProfiles(source.getActiveProfiles());
        for (String variable : LocalEnvironmentValidator.REQUIRED_VARIABLES) {
            String value = source.getProperty(variable);
            if (!variable.equals(name) && value != null) {
                copy.setProperty(variable, value);
            }
        }
        return copy;
    }
}
