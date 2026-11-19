package com.kuador.medical.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-008 end to end: undeclared handlers are rejected by the running application,
 * and every rejection is a Problem Details response with a stable code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DenyByDefaultIT.UndeclaredController.class, DenyByDefaultIT.ProtectedController.class})
class DenyByDefaultIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void controllerWithoutPermissionDeclarationRespondsForbidden() {
        assertThat(mvc.get().uri("/api/v1/test/undeclared"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("FORBIDDEN"))
                .hasPathSatisfying("$.status", status -> assertThat(status).asNumber().isEqualTo(403))
                .hasPathSatisfying("$.title", title -> assertThat(title).asString().isEqualTo("Forbidden"))
                .hasPathSatisfying("$.type", type -> assertThat(type).asString().isEqualTo("about:blank"));
    }

    @Test
    void permissionEndpointWithoutAuthenticationRespondsUnauthenticated() {
        assertThat(mvc.get().uri("/api/v1/test/protected"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("UNAUTHENTICATED"))
                .hasPathSatisfying("$.type", type -> assertThat(type).asString().isEqualTo("about:blank"));
    }

    @Test
    void unknownRouteRespondsNotFoundProblem() {
        assertThat(mvc.get().uri("/api/v1/does-not-exist"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("NOT_FOUND"))
                .hasPathSatisfying("$.type", type -> assertThat(type).asString().isEqualTo("about:blank"));
    }

    @Test
    void unsupportedMethodOnPublicRouteIsAClientErrorProblem() {
        assertThat(mvc.delete().uri("/api/v1/system/info"))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"));
    }

    @RestController
    static class UndeclaredController {

        @GetMapping("/test/undeclared")
        String undeclared() {
            return "should never be returned";
        }
    }

    @RestController
    static class ProtectedController {

        @RequiresPermission("roles.read")
        @GetMapping("/test/protected")
        String protectedResource() {
            return "should never be returned";
        }
    }
}
