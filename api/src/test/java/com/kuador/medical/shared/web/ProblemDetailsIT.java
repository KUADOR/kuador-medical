package com.kuador.medical.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Errors are Problem Details (RFC 9457) with a stable {@code code} and no internal details.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ProblemDetailsIT.FixtureController.class})
class ProblemDetailsIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void invalidBodyRespondsValidationWithFieldErrors() {
        assertThat(mvc.post().uri("/api/v1/test/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"toolong\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"))
                .hasPathSatisfying("$.errors[?(@.field == 'name')].code", c -> assertThat(c).asArray().containsExactly("REQUIRED"))
                .hasPathSatisfying("$.errors[?(@.field == 'code')].code", c -> assertThat(c).asArray().containsExactly("INVALID_LENGTH"));
    }

    @Test
    void malformedJsonRespondsValidation() {
        assertThat(mvc.post().uri("/api/v1/test/problems").contentType(MediaType.APPLICATION_JSON).content("{"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"));
    }

    @Test
    void domainExceptionKeepsItsStatusAndCode() {
        assertThat(mvc.get().uri("/api/v1/test/problems/conflict"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ROLE_NAME_TAKEN"));
    }

    @Test
    void unexpectedErrorRespondsInternalErrorWithoutLeakingDetails() throws Exception {
        var result = mvc.get().uri("/api/v1/test/problems/boom").exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INTERNAL_ERROR"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-internal-detail");
    }

    record FixtureRequest(@NotBlank String name, @Size(max = 3) String code) {}

    @RestController
    @PublicEndpoint
    static class FixtureController {

        @PostMapping("/test/problems")
        String create(@Valid @RequestBody FixtureRequest request) {
            return request.name();
        }

        @GetMapping("/test/problems/conflict")
        String conflict() {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ROLE_NAME_TAKEN, "Role name already in use.");
        }

        @GetMapping("/test/problems/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }
}
