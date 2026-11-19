package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Minimal response check against {@code contracts/identity.openapi.yaml} (ADR-011): required
 * properties, no undeclared properties and enum values. Enough for the flat schemas of the
 * skeleton; a full OpenAPI 3.1 validator can replace it when the contract grows.
 */
public final class ContractSchema {

    private static final Path CONTRACT = Path.of("..", "contracts", "identity.openapi.yaml");

    private final Map<String, Object> document;

    private ContractSchema(Map<String, Object> document) {
        this.document = document;
    }

    public static ContractSchema identity() {
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            return new ContractSchema(new Yaml().load(in));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read contract " + CONTRACT.toAbsolutePath(), e);
        }
    }

    /** Asserts that {@code body} matches the JSON schema of the given operation response. */
    public void assertResponse(String path, String method, int status, String mediaType, Map<String, Object> body) {
        Map<String, Object> response = map(map(map(map(document, "paths"), path), method.toLowerCase()), "responses");
        Map<String, Object> declared = resolve(map(response, String.valueOf(status)));
        Map<String, Object> schema = resolve(map(map(map(declared, "content"), mediaType), "schema"));
        assertObject(schema, body, path);
    }

    @SuppressWarnings("unchecked")
    private void assertObject(Map<String, Object> schema, Map<String, Object> body, String where) {
        Map<String, Object> properties = map(schema, "properties");
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        assertThat(body).as("required properties of %s", where).containsKeys(required.toArray(String[]::new));
        assertThat(properties.keySet()).as("declared properties of %s", where).containsAll(body.keySet());
        body.forEach((name, value) -> {
            Map<String, Object> property = resolve((Map<String, Object>) properties.get(name));
            if (property.containsKey("enum")) {
                assertThat((List<Object>) property.get("enum")).as("enum of %s.%s", where, name).contains(value);
            }
            if ("string".equals(property.get("type"))) {
                assertThat(value).as("type of %s.%s", where, name).isInstanceOf(String.class);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolve(Map<String, Object> node) {
        Object ref = node.get("$ref");
        if (ref == null) {
            return node;
        }
        Map<String, Object> current = document;
        for (String segment : ref.toString().substring(2).split("/")) {
            current = (Map<String, Object>) current.get(segment);
        }
        return resolve(current);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> node, String key) {
        Object value = node.get(key);
        assertThat(value).as("contract node '%s'", key).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
