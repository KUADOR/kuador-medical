package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.security.PublicEndpoints;
import com.kuador.medical.shared.security.RequiresPermission;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Constitution II and plan decision 4 of MEDK-26: the public endpoints are an explicit list
 * ({@link PublicEndpoints}) and every other handler declares that it needs an authenticated caller
 * or a permission. A new {@code @PublicEndpoint} fails here until it is added to the list on purpose.
 * The rejection of undeclared handlers at runtime is covered by {@code DenyByDefaultIT}.
 */
class EndpointPermissionTest {

    private static final String API_PREFIX = "/api/v1";

    @Test
    void loginAndHealthArePublic() {
        assertThat(PublicEndpoints.ROUTES).contains("POST /api/v1/auth/login", "GET /actuator/health");
    }

    @Test
    void sessionAndBusinessEndpointsAreNotPublic() {
        assertThat(PublicEndpoints.ROUTES)
                .doesNotContain("GET /api/v1/me", "GET /api/v1/roles", "GET /api/v1/patients", "GET /api/v1/staff-users");
    }

    @Test
    void publicHandlersAreExactlyTheExplicitList() {
        Set<String> publicHandlers = new TreeSet<>();
        EndpointPermissionContractTest.handlers().forEach((route, handler) -> {
            if (declares(handler, PublicEndpoint.class)) {
                publicHandlers.add(withPrefix(route));
            }
        });
        Set<String> listedApiRoutes = new TreeSet<>(PublicEndpoints.ROUTES);
        listedApiRoutes.removeIf(route -> !route.contains(" " + API_PREFIX + "/"));

        assertThat(publicHandlers).containsExactlyInAnyOrderElementsOf(listedApiRoutes);
    }

    @Test
    void everyOtherHandlerRequiresAnAuthenticatedCallerOrAPermission() {
        Map<String, Method> handlers = EndpointPermissionContractTest.handlers();
        assertThat(handlers).isNotEmpty();
        handlers.forEach((route, handler) -> {
            if (PublicEndpoints.ROUTES.contains(withPrefix(route))) {
                return;
            }
            assertThat(declares(handler, AuthenticatedEndpoint.class) || declares(handler, RequiresPermission.class))
                    .as("access declared by %s", route)
                    .isTrue();
            assertThat(declares(handler, PublicEndpoint.class)).as("%s is public", route).isFalse();
        });
    }

    private static String withPrefix(String route) {
        int space = route.indexOf(' ');
        return route.substring(0, space) + " " + API_PREFIX + route.substring(space + 1);
    }

    private static boolean declares(Method handler, Class<? extends Annotation> annotation) {
        return handler.isAnnotationPresent(annotation) || handler.getDeclaringClass().isAnnotationPresent(annotation);
    }
}
