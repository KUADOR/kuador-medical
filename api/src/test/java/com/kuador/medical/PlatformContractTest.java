package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Conventions of {@code contracts/platform.openapi.yaml} (MEDK-49 plan § Contrato de API,
 * ADR-007, ADR-008, ADR-011, ADR-014): every planned operation exists, platform operations
 * require a platform permission and are the only ones that accept a tenant identifier, public
 * operations are marked, creations are idempotent, errors are Problem Details and the error
 * codes extend the ones of the api.
 */
class PlatformContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "platform.openapi.yaml");
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String IDEMPOTENCY_KEY_REF = "#/components/parameters/IdempotencyKey";

    /** Operations required by the plan, as "METHOD path" relative to the {@code /api/v1} server. */
    private static final List<String> PLANNED_OPERATIONS = List.of(
            "GET /platform/plans",
            "POST /platform/plans",
            "GET /platform/plans/{planId}",
            "PUT /platform/plans/{planId}",
            "POST /platform/plans/{planId}/archive",
            "GET /platform/plans/{planId}/prices",
            "POST /platform/plans/{planId}/prices",
            "GET /platform/catalog/modules",
            "GET /platform/catalog/limits",
            "GET /platform/settings",
            "PUT /platform/settings",
            "GET /platform/tenants",
            "POST /platform/tenants",
            "GET /platform/tenants/{tenantId}",
            "PATCH /platform/tenants/{tenantId}",
            "POST /platform/tenants/{tenantId}/owner-invitations",
            "POST /platform/tenants/{tenantId}/owner/two-factor-reset",
            "GET /platform/tenants/{tenantId}/subscription",
            "POST /platform/tenants/{tenantId}/subscription/plan-change-preview",
            "POST /platform/tenants/{tenantId}/subscription/plan-changes",
            "POST /platform/tenants/{tenantId}/subscription/transitions",
            "PUT /platform/tenants/{tenantId}/subscription/price-lock",
            "POST /platform/tenants/{tenantId}/purge-requests",
            "POST /platform/tenants/{tenantId}/purge-requests/{requestId}/confirm",
            "POST /public/owner-invitations/preview",
            "POST /public/owner-invitations/accept",
            "POST /auth/two-factor/enrollment",
            "POST /auth/two-factor/enrollment/confirm",
            "POST /auth/two-factor/verify",
            "GET /tenant/subscription",
            "POST /tenant/exports",
            "GET /tenant/exports/{exportId}");

    /** Permission codes introduced by the plan; MEDK-65 adds them to {@code Permission}. */
    private static final Set<String> PLATFORM_PERMISSIONS = Set.of(
            "platform.plans.manage",
            "platform.tenants.manage",
            "platform.tenants.purge",
            "platform.settings.manage");

    private static final Set<String> CLINIC_PERMISSIONS = Set.of("subscription.read", "tenant.data.export");

    /** Error codes of the plan; PLAN_LIMIT_EXCEEDED and LAST_OWNER_PROTECTED keep their published names. */
    private static final List<String> PLANNED_ERROR_CODES = List.of(
            "TENANT_READ_ONLY",
            "PLAN_LIMIT_REACHED",
            "MODULE_NOT_IN_PLAN",
            "INVALID_SUBSCRIPTION_TRANSITION",
            "REACTIVATION_WINDOW_CLOSED",
            "INVITATION_INVALID_OR_EXPIRED",
            "LAST_OWNER",
            "TWO_FACTOR_REQUIRED",
            "RUC_ALREADY_REGISTERED");

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

        boolean isPlatform() {
            return path.startsWith("/platform/");
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
    void declaresExactlyThePlannedOperations() throws IOException {
        List<String> declared = operations().map(Operation::id).toList();

        assertThat(declared).containsExactlyInAnyOrderElementsOf(PLANNED_OPERATIONS);
    }

    @ParameterizedTest
    @MethodSource("operations")
    void platformOperationsRequireAPlatformPermissionAndOnlyThem(Operation operation) {
        Object required = operation.node().get("x-required-permission");

        assertThat(required).as("x-required-permission of %s", operation).isInstanceOf(String.class);
        if (operation.isPlatform()) {
            assertThat(PLATFORM_PERMISSIONS).as("permission of %s", operation).contains((String) required);
        } else {
            assertThat(Stream.concat(Stream.of("public", "authenticated"), CLINIC_PERMISSIONS.stream()))
                    .as("permission of %s", operation)
                    .contains((String) required);
        }
    }

    @ParameterizedTest
    @MethodSource("operations")
    void marksPublicOperationsAndOnlyThem(Operation operation) {
        boolean isPublic = "public".equals(operation.node().get("x-required-permission"));

        assertThat(isPublic).as("public operation %s", operation).isEqualTo(operation.path().startsWith("/public/"));
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
        if (!"post".equals(operation.method()) || !(responses.containsKey("201") || responses.containsKey("202"))) {
            return;
        }
        List<Object> parameters = new ArrayList<>(operation.pathParameters());
        parameters.addAll(list(operation.node().get("parameters")));
        assertThat(parameters)
                .as("Idempotency-Key of %s", operation)
                .contains(Map.of("$ref", IDEMPOTENCY_KEY_REF));

        Map<String, Object> schema = requestSchema(operation);
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
    void onlyPlatformOperationsAcceptATenantIdentifier(Operation operation) {
        List<Object> parameters = new ArrayList<>(operation.pathParameters());
        parameters.addAll(list(operation.node().get("parameters")));
        boolean takesTenantId = parameters.stream()
                .anyMatch(parameter -> "tenantId".equals(resolve(cast(parameter)).get("name")));
        assertThat(takesTenantId && !operation.isPlatform()).as("tenantId parameter of %s", operation).isFalse();

        if (operation.node().containsKey("requestBody")) {
            Object properties = requestSchema(operation).get("properties");
            if (properties != null) {
                assertThat(cast(properties)).as("request body of %s", operation).doesNotContainKey("tenantId");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("operations")
    void oneTimeTokensNeverTravelInPathsOrQueries(Operation operation) {
        List<Object> parameters = new ArrayList<>(operation.pathParameters());
        parameters.addAll(list(operation.node().get("parameters")));

        assertThat(operation.path()).as("path of %s", operation).doesNotContain("{token}");
        parameters.forEach(parameter ->
                assertThat(resolve(cast(parameter)).get("name")).as("parameter of %s", operation).isNotEqualTo("token"));
    }

    @Test
    void errorCodesExtendTheCodeAndIncludeThePlannedOnes() {
        List<Object> contractCodes = list(schema("ErrorCode").get("enum"));
        List<String> codeCodes = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();

        assertThat(contractCodes).containsAll(codeCodes).containsAll(PLANNED_ERROR_CODES).doesNotHaveDuplicates();
    }

    @Test
    void reactivationFromCancelledIsDocumentedOnTheTransitionOperation() {
        Map<String, Object> transitions = cast(map(map(document, "paths"),
                "/platform/tenants/{tenantId}/subscription/transitions").get("post"));
        Map<String, Object> conflict = resolve(cast(map(transitions, "responses").get("409")));
        Map<String, Object> examples = map(map(map(conflict, "content"), PROBLEM_JSON), "examples");

        assertThat(examples.values())
                .extracting(example -> cast(cast(example).get("value")).get("code"))
                .contains("INVALID_SUBSCRIPTION_TRANSITION", "REACTIVATION_WINDOW_CLOSED");
    }

    private static Map<String, Object> requestSchema(Operation operation) {
        return resolve(map(map(map(map(operation.node(), "requestBody"), "content"), "application/json"), "schema"));
    }

    private static Map<String, Object> schema(String name) {
        return map(map(map(document, "components"), "schemas"), name);
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
