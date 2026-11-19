package com.kuador.medical.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.shared.capability.Capability;
import com.kuador.medical.shared.capability.CapabilityChecker;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * Deny-by-default rules of ADR-008, checked on the interceptor without a Spring context.
 */
class AccessControlInterceptorTest {

    private static final CapabilityChecker ALL_ENABLED = capability -> true;

    @Test
    void handlerWithoutDeclarationIsForbidden() {
        var interceptor = interceptor(anonymous(), ALL_ENABLED);

        assertDenied(interceptor, handler("undeclared"), HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    @Test
    void handlerWithoutDeclarationIsForbiddenEvenForAnAccountWithEveryPermission() {
        var interceptor = interceptor(authenticated("roles.read", "roles.create"), ALL_ENABLED);

        assertDenied(interceptor, handler("undeclared"), HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    @Test
    void handlerDeclaringMoreThanOneAccessLevelIsForbidden() {
        var interceptor = interceptor(authenticated("roles.read"), ALL_ENABLED);

        assertDenied(interceptor, handler("ambiguous"), HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    @Test
    void handlerThatIsNotAControllerMethodIsForbidden() {
        var interceptor = interceptor(authenticated("roles.read"), ALL_ENABLED);

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), new Object()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void publicEndpointIsAllowedWithoutAuthentication() throws Exception {
        var interceptor = interceptor(anonymous(), ALL_ENABLED);

        assertThat(interceptor.preHandle(request(), response(), handler("open"))).isTrue();
    }

    @Test
    void authenticatedEndpointRejectsAnonymousCallers() {
        var interceptor = interceptor(anonymous(), ALL_ENABLED);

        assertDenied(interceptor, handler("signedIn"), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void authenticatedEndpointAllowsAnyAuthenticatedCaller() throws Exception {
        var interceptor = interceptor(authenticated(), ALL_ENABLED);

        assertThat(interceptor.preHandle(request(), response(), handler("signedIn"))).isTrue();
    }

    @Test
    void permissionEndpointRejectsAnonymousCallers() {
        var interceptor = interceptor(anonymous(), ALL_ENABLED);

        assertDenied(interceptor, handler("readRoles"), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void permissionEndpointRejectsCallersWithoutThePermission() {
        var interceptor = interceptor(authenticated("roles.create"), ALL_ENABLED);

        assertDenied(interceptor, handler("readRoles"), HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    @Test
    void permissionEndpointAllowsCallersWithThePermission() throws Exception {
        var interceptor = interceptor(authenticated("roles.read"), ALL_ENABLED);

        assertThat(interceptor.preHandle(request(), response(), handler("readRoles"))).isTrue();
    }

    @Test
    void classLevelDeclarationAppliesToEveryMethod() throws Exception {
        var interceptor = interceptor(authenticated("roles.read"), ALL_ENABLED);

        assertThat(interceptor.preHandle(request(), response(), handler(ClassLevelController.class, "list")))
                .isTrue();
    }

    @Test
    void methodDeclarationOverridesClassDeclaration() throws Exception {
        var interceptor = interceptor(anonymous(), ALL_ENABLED);

        assertThat(interceptor.preHandle(request(), response(), handler(ClassLevelController.class, "open")))
                .isTrue();
    }

    @Test
    void disabledCapabilityIsForbiddenEvenOnPublicEndpoints() {
        var interceptor = interceptor(authenticated("roles.read"), capability -> false);

        assertDenied(interceptor, handler("open"), HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    @Test
    void handlerWithoutCapabilityIsForbidden() {
        var interceptor = new AccessControlInterceptor(authenticated("roles.read"), ALL_ENABLED, type -> Optional.empty());

        assertDenied(interceptor, handler("open"), HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    private static AccessControlInterceptor interceptor(AccessChecker accessChecker, CapabilityChecker capabilities) {
        return new AccessControlInterceptor(accessChecker, capabilities, type -> Optional.of(Capability.PLATFORM));
    }

    private static void assertDenied(
            AccessControlInterceptor interceptor, HandlerMethod handler, HttpStatus status, ErrorCode code) {
        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.code()).isEqualTo(code);
                });
    }

    private static HandlerMethod handler(String method) {
        return handler(FixtureController.class, method);
    }

    private static HandlerMethod handler(Class<?> type, String method) {
        try {
            return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getDeclaredMethod(method));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/fixture");
    }

    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    private static AccessChecker anonymous() {
        return new FixedAccessChecker(false, Set.of());
    }

    private static AccessChecker authenticated(String... permissions) {
        return new FixedAccessChecker(true, Set.of(permissions));
    }

    private record FixedAccessChecker(boolean authenticated, Set<String> permissions) implements AccessChecker {

        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public boolean hasPermission(String permission) {
            return authenticated && permissions.contains(permission);
        }
    }

    static class FixtureController {

        public void undeclared() {}

        @PublicEndpoint
        public void open() {}

        @AuthenticatedEndpoint
        public void signedIn() {}

        @RequiresPermission("roles.read")
        public void readRoles() {}

        @PublicEndpoint
        @RequiresPermission("roles.read")
        public void ambiguous() {}
    }

    @RequiresPermission("roles.read")
    static class ClassLevelController {

        public void list() {}

        @PublicEndpoint
        public void open() {}
    }
}
