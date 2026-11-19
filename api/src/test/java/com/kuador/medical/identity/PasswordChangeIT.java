package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/v1/auth/password: minimum policy (12 characters, different from the current one),
 * the current password is required and the change is audited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordChangeIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;
    private String email;
    private String password;
    private String token;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        email = IdentityApi.uniqueEmail("owner");
        password = IdentityApi.randomPassword();
        api.signUp("Clinica Claves", email, password);
        token = api.token(email, password);
    }

    @Test
    void changedPasswordReplacesTheOldOne() {
        String newPassword = IdentityApi.randomPassword();

        assertThat(api.changePassword(token, password, newPassword)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(api.login(email, password)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.login(email, newPassword)).hasStatus(HttpStatus.OK);
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        assertThat(api.changePassword(token, password + "x", IdentityApi.randomPassword()))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INVALID_CREDENTIALS"));
        assertThat(api.login(email, password)).hasStatus(HttpStatus.OK);
    }

    @Test
    void passwordShorterThanTwelveCharactersIsWeak() {
        assertThat(api.changePassword(token, password, "elevenchars"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("WEAK_PASSWORD"));
    }

    @Test
    void reusingTheCurrentPasswordIsWeak() {
        assertThat(api.changePassword(token, password, password))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("WEAK_PASSWORD"));
    }

    @Test
    void passwordOfExactlyTwelveCharactersIsAccepted() {
        assertThat(api.changePassword(token, password, "twelve-chars")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void changeRequiresAuthentication() {
        assertThat(mvc.post().uri("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changeIsAudited() {
        UUID accountId = jdbc.queryForObject("select id from user_account where email = ?", UUID.class, email);

        api.changePassword(token, password, IdentityApi.randomPassword());

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'PASSWORD_CHANGED' and actor_account_id = ? and tenant_id is not null",
                Integer.class, accountId)).isOne();
    }

    @Test
    void errorsMatchTheContract() {
        ContractSchema contract = ContractSchema.identity();

        contract.assertResponse("/auth/password", "POST", 400, "application/problem+json",
                api.body(api.changePassword(token, password, "short")));
        contract.assertResponse("/auth/password", "POST", 401, "application/problem+json",
                api.body(api.changePassword(token, password + "x", IdentityApi.randomPassword())));
    }
}
