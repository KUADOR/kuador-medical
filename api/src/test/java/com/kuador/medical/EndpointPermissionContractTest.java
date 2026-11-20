package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.security.RequiresPermission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Build-breaking permission rules of the handlers (ADR-008, Constitution II), next to
 * {@link ArchitectureTests}: every declared permission exists in the catalog, and a handler that
 * implements an operation of a contract declares exactly the access of its
 * {@code x-required-permission}.
 */
class EndpointPermissionContractTest {

    private static final List<Path> CONTRACTS = List.of(
            Path.of("..", "contracts", "user-registration.openapi.yaml"),
            Path.of("..", "contracts", "identity.openapi.yaml"));

    @Test
    void everyRequiredPermissionBelongsToTheCatalog() {
        handlers().forEach((operation, handler) -> {
            RequiresPermission permission = declared(handler, RequiresPermission.class);
            if (permission != null) {
                assertThat(Permission.fromCode(permission.value()))
                        .as("permission of %s", operation)
                        .isPresent();
            }
        });
    }

    @Test
    void handlersDeclareThePermissionOfTheirContractOperation() throws IOException {
        Map<String, String> required = new HashMap<>();
        for (Path contract : CONTRACTS.reversed()) {
            required.putAll(requiredPermissions(contract));
        }
        Map<String, Method> handlers = handlers();

        assertThat(handlers).containsKeys(
                "GET /permissions", "GET /roles", "POST /roles", "GET /roles/{id}", "PUT /roles/{id}",
                "DELETE /roles/{id}", "PUT /staff-users/{id}/role-assignments",
                "POST /auth/login", "POST /auth/login/clinic", "POST /auth/login/branch", "POST /auth/refresh",
                "POST /auth/logout", "POST /auth/password-reset/request", "POST /auth/password-reset/confirm",
                "POST /auth/switch-branch", "GET /me",
                "GET /patients", "POST /patients", "GET /patients/{id}", "PATCH /patients/{id}",
                "POST /patients/{id}/consents", "GET /patients/{id}/guardian-links",
                "POST /patients/{id}/guardian-links", "POST /patients/{id}/guardian-links/{linkId}/end",
                "POST /patients/{id}/account-invitation", "GET /patient-registration-settings",
                "PUT /patient-registration-settings", "POST /public/clinics/{publicCode}/patient-registrations",
                "POST /auth/email-verification/confirm", "GET /me/guardian-links", "POST /me/guardian-links");
        handlers.forEach((operation, handler) -> {
            if (required.containsKey(operation)) {
                assertThat(accessOf(handler)).as("access of %s", operation).isEqualTo(required.get(operation));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> requiredPermissions(Path contract) throws IOException {
        Map<String, Object> document;
        try (InputStream in = Files.newInputStream(contract)) {
            document = new Yaml().load(in);
        }
        Map<String, String> result = new HashMap<>();
        ((Map<String, Object>) document.get("paths")).forEach((path, item) ->
                ((Map<String, Object>) item).forEach((method, node) -> {
                    if (node instanceof Map<?, ?> operation && operation.containsKey("x-required-permission")) {
                        result.put(method.toUpperCase() + " " + path, operation.get("x-required-permission").toString());
                    }
                }));
        return result;
    }

    /** Handlers of the application by {@code "METHOD /path"}, without the {@code /api/v1} prefix. */
    private static Map<String, Method> handlers() {
        Map<String, Method> handlers = new HashMap<>();
        for (JavaClass javaClass : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kuador.medical")) {
            if (!javaClass.isAnnotatedWith(RestController.class)) {
                continue;
            }
            Class<?> controller = javaClass.reflect();
            RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String prefix = onClass == null || onClass.path().length == 0 ? "" : onClass.path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String path = prefix + (mapping.path().length == 0 ? "" : mapping.path()[0]);
                for (var httpMethod : mapping.method()) {
                    handlers.put(httpMethod.name() + " " + path, method);
                }
            }
        }
        return handlers;
    }

    private static String accessOf(Method handler) {
        if (declared(handler, PublicEndpoint.class) != null) {
            return "public";
        }
        if (declared(handler, AuthenticatedEndpoint.class) != null) {
            return "authenticated";
        }
        RequiresPermission permission = declared(handler, RequiresPermission.class);
        return permission == null ? "undeclared" : permission.value();
    }

    private static <A extends java.lang.annotation.Annotation> @Nullable A declared(Method handler, Class<A> type) {
        A onMethod = handler.getAnnotation(type);
        if (onMethod != null) {
            return onMethod;
        }
        AnnotatedElement owner = handler.getDeclaringClass();
        return owner.getAnnotation(type);
    }
}
