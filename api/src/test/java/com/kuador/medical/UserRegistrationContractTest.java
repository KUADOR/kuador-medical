package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.shared.web.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Conventions of {@code contracts/user-registration.openapi.yaml} (MEDK-12 plan § Contrato,
 * ADR-007, ADR-008, ADR-011): every operation declares its permission, public operations are
 * marked and unauthenticated, creations are idempotent, errors are Problem Details and the
 * enumerations match the code.
 */
class UserRegistrationContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "user-registration.openapi.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String IDEMPOTENCY_KEY_REF = "#/components/parameters/IdempotencyKey";

    /** Operations required by the plan, as "METHOD path" relative to the {@code /api/v1} server. */
    private static final List<String> PLANNED_OPERATIONS = List.of(
            "POST /auth/login",
            "POST /auth/login/clinic",
            "POST /auth/login/branch",
            "POST /auth/refresh",
            "POST /auth/logout",
            "POST /auth/password-reset/request",
            "POST /auth/password-reset/confirm",
            "POST /auth/invitations/preview",
            "POST /auth/invitations/accept",
            "POST /auth/email-verification/confirm",
            "POST /auth/switch-branch",
            "GET /me",
            "GET /staff-users",
            "POST /staff-users",
            "GET /staff-users/{id}",
            "PATCH /staff-users/{id}",
            "POST /staff-users/{id}/invitation",
            "POST /staff-users/{id}/disable",
            "POST /staff-users/{id}/enable",
            "PUT /staff-users/{id}/role-assignments",
            "GET /permissions",
            "GET /roles",
            "POST /roles",
            "GET /roles/{id}",
            "PUT /roles/{id}",
            "DELETE /roles/{id}",
            "GET /patients",
            "POST /patients",
            "GET /patients/{id}",
            "PATCH /patients/{id}",
            "POST /patients/{id}/consents",
            "GET /patients/{id}/guardian-links",
            "POST /patients/{id}/guardian-links",
            "POST /patients/{id}/guardian-links/{linkId}/end",
            "POST /patients/{id}/account-invitation",
            "GET /me/guardian-links",
            "POST /me/guardian-links",
            "GET /patient-registration-settings",
            "PUT /patient-registration-settings",
            "POST /public/clinics/{publicCode}/patient-registrations");

    private static Map<String, Object> document;

    @BeforeAll
    static void loadContract() throws IOException {
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            document = new Yaml().load(in);
        }
    }

    record Operation(String method, String path, Map<String, Object> node, List<Object> pathParameters) {

        String id() {
            return method.toUpperCase() + " " + path;
        }

        @Override
        public String toString() {
            return id();
        }
    }

    static Stream<Operation> operations() throws IOException {
        if (document == null) {
            loadContract();
        }
        List<Operation> operations = new ArrayList<>();
        map(document, "paths").forEach((path, item) -> {
            Map<String, Object> pathItem = cast(item);
            List<Object> pathParameters = list(pathItem.get("parameters"));
            pathItem.forEach((key, value) -> {
                if (METHODS.contains(key)) {
                    operations.add(new Operation(key, path, cast(value), pathParameters));
                }
            });
        });
        return operations.stream();
    }

    @Test
    void declaresEveryPlannedOperation() throws IOException {
        List<String> declared = operations().map(Operation::id).toList();

        assertThat(declared).containsAll(PLANNED_OPERATIONS);
    }

    @ParameterizedTest
    @MethodSource("operations")
    void declaresARequiredPermissionFromTheCatalog(Operation operation) {
        Object required = operation.node().get("x-required-permission");

        assertThat(required).as("x-required-permission of %s", operation).isInstanceOf(String.class);
        String code = (String) required;
        if (!code.equals("public") && !code.equals("authenticated")) {
            assertThat(Permission.fromCode(code))
                    .as("permission %s of %s", code, operation)
                    .hasValueSatisfying(permission ->
                            assertThat(permission.scope()).isEqualTo(PermissionScope.TENANT));
        }
    }

    @ParameterizedTest
    @MethodSource("operations")
    void marksPublicOperationsAndOnlyThem(Operation operation) {
        boolean isPublic = "public".equals(operation.node().get("x-required-permission"));

        if (isPublic) {
            assertThat(operation.node()).as("x-public of %s", operation).containsEntry("x-public", true);
            assertThat(operation.node()).as("security of %s", operation).containsEntry("security", List.of());
        } else {
            assertThat(operation.node()).as("x-public of %s", operation).doesNotContainKey("x-public");
            assertThat(operation.node()).as("security of %s", operation).doesNotContainKey("security");
        }
    }

    @ParameterizedTest
    @MethodSource("operations")
    void creationsAcceptAnIdempotencyKeyAndAClientGeneratedId(Operation operation) {
        Map<String, Object> responses = map(operation.node(), "responses");
        boolean creates = "post".equals(operation.method())
                && (responses.containsKey("201") || operation.path().endsWith("/patient-registrations"));
        if (!creates) {
            return;
        }
        List<Object> parameters = new ArrayList<>(operation.pathParameters());
        parameters.addAll(list(operation.node().get("parameters")));
        assertThat(parameters)
                .as("Idempotency-Key of %s", operation)
                .contains(Map.of("$ref", IDEMPOTENCY_KEY_REF));

        Map<String, Object> schema = resolve(map(map(map(
                map(operation.node(), "requestBody"), "content"), "application/json"), "schema"));
        Map<String, Object> id = resolve(map(map(schema, "properties"), "id"));
        assertThat(id).as("client id of %s", operation).containsEntry("format", "uuid");
        assertThat(list(schema.get("required"))).as("required of %s", operation).doesNotContain("id");
    }

    @ParameterizedTest
    @MethodSource("operations")
    void errorsAreProblemDetails(Operation operation) {
        map(operation.node(), "responses").forEach((status, response) -> {
            if (status.startsWith("4") || status.startsWith("5")) {
                Map<String, Object> content = map(resolve(cast(response)), "content");
                assertThat(content.keySet()).as("media types of %s %s", operation, status).containsExactly(PROBLEM_JSON);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("operations")
    void neverAcceptsATenantIdentifier(Operation operation) {
        List<Object> parameters = new ArrayList<>(operation.pathParameters());
        parameters.addAll(list(operation.node().get("parameters")));
        parameters.forEach(parameter ->
                assertThat(resolve(cast(parameter)).get("name")).as("parameter of %s", operation).isNotEqualTo("tenantId"));

        Object body = operation.node().get("requestBody");
        if (body != null) {
            Map<String, Object> schema = resolve(map(map(map(cast(body), "content"), "application/json"), "schema"));
            Object properties = schema.get("properties");
            if (properties != null) {
                assertThat(cast(properties)).as("request body of %s", operation).doesNotContainKey("tenantId");
            }
        }
    }

    @Test
    void errorCodesMatchTheCode() {
        List<Object> contractCodes = list(map(map(map(document, "components"), "schemas"), "ErrorCode").get("enum"));
        List<String> codeCodes = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();

        // platform.openapi.yaml extends this list (MEDK-49); PlatformContractTest checks that it
        // publishes every code of the api, so here the api only has to know every published code.
        assertThat(codeCodes).containsAll(contractCodes.stream().map(Object::toString).toList());
        assertThat(contractCodes).doesNotHaveDuplicates();
    }

    @Test
    void permissionCodesMatchTheCatalog() {
        List<Object> contractCodes =
                list(map(map(map(document, "components"), "schemas"), "PermissionCode").get("enum"));
        List<String> catalog = Arrays.stream(Permission.values()).map(Permission::code).toList();

        assertThat(contractCodes).containsExactlyElementsOf(catalog);
    }

    private static Map<String, Object> resolve(Map<String, Object> node) {
        Object ref = node.get("$ref");
        if (ref == null) {
            return node;
        }
        Map<String, Object> current = document;
        for (String segment : ref.toString().substring(2).split("/")) {
            current = map(current, segment);
        }
        return resolve(current);
    }

    private static Map<String, Object> map(Map<String, Object> node, String key) {
        Object value = node.get(key);
        assertThat(value).as("contract node '%s'", key).isInstanceOf(Map.class);
        return cast(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }
}
