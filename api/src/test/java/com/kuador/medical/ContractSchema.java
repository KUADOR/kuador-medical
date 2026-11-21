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
 * Minimal response check against a contract of {@code contracts/} (ADR-011): required
 * properties, no undeclared properties and enum values. Enough for the flat schemas of the
 * skeleton; a full OpenAPI 3.1 validator can replace it when the contract grows.
 */
public final class ContractSchema {

    private static final Path CONTRACT = Path.of("..", "contracts", "identity.openapi.yaml");
    private static final Path USER_REGISTRATION = Path.of("..", "contracts", "user-registration.openapi.yaml");
    private static final Path PLATFORM = Path.of("..", "contracts", "platform.openapi.yaml");

    private final Map<String, Object> document;

    private ContractSchema(Map<String, Object> document) {
        this.document = document;
    }

    public static ContractSchema identity() {
        return load(CONTRACT);
    }

    /** {@code contracts/user-registration.openapi.yaml}, the target contract of epic MEDK-12. */
    public static ContractSchema userRegistration() {
        return load(USER_REGISTRATION);
    }

    /** {@code contracts/platform.openapi.yaml}, the target contract of epic MEDK-49. */
    public static ContractSchema platform() {
        return load(PLATFORM);
    }

    private static ContractSchema load(Path contract) {
        try (InputStream in = Files.newInputStream(contract)) {
            return new ContractSchema(new Yaml().load(in));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read contract " + contract.toAbsolutePath(), e);
        }
    }

    /** Asserts that {@code body} matches the JSON schema of the given operation response. */
    public void assertResponse(String path, String method, int status, String mediaType, Map<String, Object> body) {
        Map<String, Object> response = map(map(map(map(document, "paths"), path), method.toLowerCase()), "responses");
        Map<String, Object> declared = resolve(map(response, String.valueOf(status)));
        Map<String, Object> schema = resolve(map(map(map(declared, "content"), mediaType), "schema"));
        assertObject(schema, body, path);
    }

    /** Asserts that {@code body} matches an array response schema, item by item. */
    public void assertArrayResponse(
            String path, String method, int status, String mediaType, List<Map<String, Object>> body) {
        Map<String, Object> response = map(map(map(map(document, "paths"), path), method.toLowerCase()), "responses");
        Map<String, Object> declared = resolve(map(response, String.valueOf(status)));
        Map<String, Object> schema = resolve(map(map(map(declared, "content"), mediaType), "schema"));
        assertThat(schema.get("type")).as("type of %s", path).isEqualTo("array");
        Map<String, Object> items = resolve(map(schema, "items"));
        body.forEach(item -> assertObject(items, item, path + "[]"));
    }

    @SuppressWarnings("unchecked")
    private void assertObject(Map<String, Object> schema, Map<String, Object> body, String where) {
        if (schema.containsKey("oneOf")) {
            assertObject(variant(schema, body, where), body, where);
            return;
        }
        if (schema.containsKey("allOf")) {
            assertObject(merged((List<Map<String, Object>>) schema.get("allOf")), body, where);
            return;
        }
        Map<String, Object> properties = map(schema, "properties");
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        assertThat(body).as("required properties of %s", where).containsKeys(required.toArray(String[]::new));
        assertThat(properties.keySet()).as("declared properties of %s", where).containsAll(body.keySet());
        body.forEach((name, value) -> {
            Map<String, Object> property = resolve((Map<String, Object>) properties.get(name));
            if (property.containsKey("enum")) {
                assertThat((List<Object>) property.get("enum")).as("enum of %s.%s", where, name).contains(value);
            }
            if (property.containsKey("const")) {
                assertThat(value).as("const of %s.%s", where, name).isEqualTo(property.get("const"));
            }
            if ("string".equals(property.get("type"))) {
                assertThat(value).as("type of %s.%s", where, name).isInstanceOf(String.class);
            }
        });
    }

    /** The object schemas of an {@code allOf}, merged: their properties and required properties together. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> merged(List<Map<String, Object>> parts) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        List<String> required = new java.util.ArrayList<>();
        for (Map<String, Object> part : parts) {
            Map<String, Object> schema = resolve(part);
            properties.putAll((Map<String, Object>) schema.getOrDefault("properties", Map.of()));
            required.addAll((List<String>) schema.getOrDefault("required", List.of()));
        }
        return Map.of("properties", properties, "required", required);
    }

    /** The {@code oneOf} variant named by the discriminator value of {@code body}. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> variant(Map<String, Object> schema, Map<String, Object> body, String where) {
        Map<String, Object> discriminator = map(schema, "discriminator");
        Object value = body.get(discriminator.get("propertyName").toString());
        Object ref = map(discriminator, "mapping").get(String.valueOf(value));
        assertThat(ref).as("discriminator value %s of %s", value, where).isNotNull();
        return resolve(Map.of("$ref", ref));
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
