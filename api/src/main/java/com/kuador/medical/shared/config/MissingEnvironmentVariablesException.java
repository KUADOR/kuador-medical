package com.kuador.medical.shared.config;

import java.util.List;

/** Required environment variables are missing or blank. Holds their names only, never values. */
public class MissingEnvironmentVariablesException extends IllegalStateException {

    private final List<String> variables;

    public MissingEnvironmentVariablesException(List<String> variables) {
        super("Missing required environment variables for the local profile: " + String.join(", ", variables));
        this.variables = List.copyOf(variables);
    }

    public List<String> variables() {
        return variables;
    }
}
