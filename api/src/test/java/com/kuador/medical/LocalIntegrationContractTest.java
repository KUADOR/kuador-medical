package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.PublicEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * {@code contracts/local-integration.openapi.yaml} (MEDK-26 plan, decision 4): the minimal contract
 * web and mobile use against the local api. It documents the operations the api already serves,
 * so the access of each one must match its handler, and no request may carry a tenant identifier.
 */
class LocalIntegrationContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "local-integration.openapi.yaml");
    private static final String API_PREFIX = "/api/v1";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    /** Operations of the contract, with their full path (the server is the api origin). */
    private static final List<String> OPERATIONS = List.of(
            "POST /api/v1/auth/login",
            "GET /api/v1/me",
            "GET /api/v1/system/info",
            "GET /actuator/health");

    private static Map<String, Object> contract;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            contract = new Yaml().load(in);
        }
    }

    @Test
    void declaresExactlyTheLocalIntegrationOperations() {
        assertThat(operations().keySet()).containsExactlyInAnyOrderElementsOf(OPERATIONS);
    }

    @Test
    void servesFromTheApiOriginSoTheHealthEndpointKeepsItsPath() {
        List<Map<String, Object>> servers = list(contract.get("servers"));
        assertThat(servers).extracting(server -> server.get("url")).containsExactly("/");
    }

    @Test
    void everyOperationDeclaresItsAccessAndPublicOnesHaveNoSecurity() {
        operations().forEach((operation, node) -> {
            Object required = node.get("x-required-permission");
            assertThat(required).as("x-required-permission of %s", operation).isNotNull();
            if ("public".equals(required)) {
                assertThat(node.get("x-public")).as("x-public of %s", operation).isEqualTo(true);
                assertThat(list(node.get("security"))).as("security of %s", operation).isEmpty();
            }
        });
    }

    @Test
    void implementedOperationsDeclareTheAccessOfTheirHandlers() {
        Map<String, Method> handlers = EndpointPermissionContractTest.handlers();
        operations().forEach((operation, node) -> {
            if (!operation.contains(" " + API_PREFIX + "/")) {
                return;
            }
            String relative = operation.replace(" " + API_PREFIX + "/", " /");
            assertThat(handlers).as("handler of %s", operation).containsKey(relative);
            assertThat(accessOf(handlers.get(relative)))
                    .as("access of %s", operation)
                    .isEqualTo(node.get("x-required-permission"));
        });
    }

    @Test
    void loginAndMeDocumentUnauthorizedAndForbiddenAsProblemDetails() {
        for (String operation : List.of("POST /api/v1/auth/login", "GET /api/v1/me")) {
            Map<String, Object> responses = map(operations().get(operation).get("responses"));
            assertThat(responses).as("responses of %s", operation).containsKeys("401", "403");
        }
    }

    @Test
    void noRequestCarriesATenantIdentifier() {
        operations().forEach((operation, node) -> {
            for (Map<String, Object> parameter : list(node.getOrDefault("parameters", List.of()))) {
                assertThat(String.valueOf(parameter.get("name")).toLowerCase(Locale.ROOT))
                        .as("parameter of %s", operation)
                        .doesNotContain("tenant")
                        .doesNotContain("clinic");
            }
        });
    }

    private static Map<String, Map<String, Object>> operations() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        map(contract.get("paths")).forEach((path, item) -> map(item).forEach((method, node) -> {
            if (METHODS.contains(method)) {
                result.put(method.toUpperCase(Locale.ROOT) + " " + path, map(node));
            }
        }));
        return result;
    }

    private static String accessOf(Method handler) {
        if (handler.isAnnotationPresent(PublicEndpoint.class)
                || handler.getDeclaringClass().isAnnotationPresent(PublicEndpoint.class)) {
            return "public";
        }
        if (handler.isAnnotationPresent(AuthenticatedEndpoint.class)
                || handler.getDeclaringClass().isAnnotationPresent(AuthenticatedEndpoint.class)) {
            return "authenticated";
        }
        return "other";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return (Map<String, Object>) node;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object node) {
        return node == null ? new ArrayList<>() : (List<Map<String, Object>>) node;
    }
}
