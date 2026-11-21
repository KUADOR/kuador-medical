package com.kuador.medical.shared.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Readable startup error for {@link MissingEnvironmentVariablesException}: names only, no values. */
public class MissingEnvironmentVariablesFailureAnalyzer
        extends AbstractFailureAnalyzer<MissingEnvironmentVariablesException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingEnvironmentVariablesException cause) {
        return new FailureAnalysis(
                "The api cannot start in the " + cause.profile() + " profile. Missing or blank environment "
                        + "variables: " + String.join(", ", cause.variables()),
                action(cause.profile()),
                cause);
    }

    private static String action(String profile) {
        if (ProdEnvironmentValidator.PROD_PROFILE.equals(profile)) {
            return "Define those variables in the environment (secrets in the environment's secret manager) "
                    + "and start again. Variables are described in docs/deployment.md.";
        }
        return "Copy api/.env.example to api/.env (or .env.example to .env at the repository root), fill in "
                + "those variables and start again. Variables are described in docs/deployment.md.";
    }
}
