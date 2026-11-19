package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/v1/auth/login and GET /api/v1/me (ADR-006): JWT access token with the scope of the
 * account; wrong credentials never reveal which part was wrong.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;
    private String email;
    private String password;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        email = IdentityApi.uniqueEmail("owner");
        password = IdentityApi.randomPassword();
        tenantId = api.signUp("Clinica Login", email, password);
    }

    @Test
    void loginReturnsATenantScopedBearerToken() {
        MvcTestResult result = api.login(email, password);

        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        assertThat(body)
                .containsEntry("tokenType", "Bearer")
                .containsEntry("expiresIn", 1800)
                .containsEntry("scope", "TENANT")
                .containsEntry("mustChangePassword", false);
        assertThat(body.get("accessToken").toString().split("\\.")).hasSize(3);
    }

    @Test
    void emailIsComparedCaseInsensitively() {
        assertThat(api.login(email.toUpperCase(), password)).hasStatus(HttpStatus.OK);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() {
        MvcTestResult wrongPassword = api.login(email, password + "x");
        MvcTestResult unknownEmail = api.login(IdentityApi.uniqueEmail("nobody"), password);

        for (MvcTestResult result : new MvcTestResult[] {wrongPassword, unknownEmail}) {
            assertThat(result)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INVALID_CREDENTIALS"));
        }
        assertThat(api.body(wrongPassword)).isEqualTo(api.body(unknownEmail));
    }

    @Test
    void successfulLoginIsAudited() {
        UUID accountId = jdbc.queryForObject("select id from identity_user_account where email_normalized = ?", UUID.class, email);

        api.token(email, password);

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'LOGIN_SUCCEEDED' and actor_account_id = ? and tenant_id = ?",
                Integer.class, accountId, tenantId)).isOne();
    }

    @Test
    void meRequiresAValidToken() {
        String token = api.token(email, password);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(mvc.get().uri("/api/v1/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        for (String invalid : new String[] {tampered, "not-a-jwt"}) {
            assertThat(api.me(invalid))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("UNAUTHENTICATED"));
        }
    }

    @Test
    void disabledAccountCannotLogInNorUseItsToken() {
        String token = api.token(email, password);

        jdbc.update("update identity_user_account set status = 'DISABLED' where email_normalized = ?", email);

        assertThat(api.login(email, password)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.me(token)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void suspendedClinicCannotLogIn() {
        jdbc.update("update tenant set status = 'SUSPENDED' where id = ?", tenantId);

        assertThat(api.login(email, password)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicEndpointsIgnoreAnInvalidToken() {
        assertThat(api.get("/api/v1/system/info", "not-a-jwt")).hasStatus(HttpStatus.OK);
    }

    @Test
    void responsesMatchTheContract() {
        ContractSchema contract = ContractSchema.identity();
        MvcTestResult login = api.login(email, password);

        contract.assertResponse("/auth/login", "POST", 200, "application/json", api.body(login));
        contract.assertResponse("/auth/login", "POST", 401, "application/problem+json",
                api.body(api.login(email, password + "x")));
        contract.assertResponse("/me", "GET", 200, "application/json",
                api.body(api.me(api.body(login).get("accessToken").toString())));
        contract.assertResponse("/me", "GET", 401, "application/problem+json", api.body(api.me("not-a-jwt")));
    }
}
