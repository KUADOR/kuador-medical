package com.kuador.medical.shared.config;

import java.util.List;

/** Required environment variables are missing or blank. Holds their names only, never values. */
public class MissingEnvironmentVariablesException extends IllegalStateException {

    private final String profile;
    private final List<String> variables;

    public MissingEnvironmentVariablesException(List<String> variables) {
        this(LocalEnvironmentValidator.LOCAL_PROFILE, variables);
    }

    public MissingEnvironmentVariablesException(String profile, List<String> variables) {
        super("Missing required environment variables for the " + profile + " profile: "
                + String.join(", ", variables));
        this.profile = profile;
        this.variables = List.copyOf(variables);
    }

    public String profile() {
        return profile;
    }

    public List<String> variables() {
        return variables;
    }
}
