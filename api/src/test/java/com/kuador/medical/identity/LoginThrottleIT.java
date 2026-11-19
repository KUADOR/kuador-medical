package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Login attempt limit (ADR-012, FR-006): 5 failures in 15 minutes per email + IP and per account
 * lock the login temporarily, with the same answer whether or not the email exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginThrottleIT {

    private static final int MAX_FAILURES = 5;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;
    private String email;
    private String password;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        email = IdentityApi.uniqueEmail("owner");
        password = IdentityApi.randomPassword();
        api.signUp("Clinica Intentos", email, password);
    }

    @Test
    void afterTooManyFailuresEvenTheRightPasswordIsRejected() {
        failFrom("198.51.100.1", email, MAX_FAILURES);

        MvcTestResult locked = loginFrom("198.51.100.1", email, password);

        assertThat(locked)
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("TOO_MANY_ATTEMPTS"));
        assertThat(Integer.parseInt(locked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
        ContractSchema.userRegistration().assertResponse("/auth/login", "POST", 429, "application/problem+json",
                api.body(locked));
    }

    @Test
    void theAccountIsLockedFromEveryAddress() {
        failFrom("198.51.100.2", email, MAX_FAILURES);

        assertThat(loginFrom("198.51.100.3", email, password)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void fewerFailuresThanTheLimitStillLetTheOwnerIn() {
        failFrom("198.51.100.4", email, MAX_FAILURES - 1);

        assertThat(loginFrom("198.51.100.4", email, password)).hasStatus(HttpStatus.OK);
        failFrom("198.51.100.4", email, MAX_FAILURES - 1);
        assertThat(loginFrom("198.51.100.4", email, password))
                .as("a successful login starts the count again")
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void anUnknownEmailIsLockedTheSameWay() {
        String unknown = IdentityApi.uniqueEmail("nobody");
        failFrom("198.51.100.5", unknown, MAX_FAILURES);

        MvcTestResult unknownLocked = loginFrom("198.51.100.5", unknown, password);
        failFrom("198.51.100.5", email, MAX_FAILURES);
        MvcTestResult knownLocked = loginFrom("198.51.100.5", email, password);

        assertThat(unknownLocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(api.body(unknownLocked)).isEqualTo(api.body(knownLocked));
    }

    @Test
    void theLockIsAuditedAndStoresNoEmail() {
        failFrom("198.51.100.6", email, MAX_FAILURES);

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'LOGIN_LOCKED' and occurred_at > now() - interval '1 minute'",
                Integer.class)).isPositive();
        assertThat(jdbc.queryForObject(
                "select count(*) from identity_login_throttle where key_hash like ?", Integer.class, "%" + email + "%"))
                .isZero();
    }

    private void failFrom(String ip, String address, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(loginFrom(ip, address, password + "-wrong")).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    private MvcTestResult loginFrom(String ip, String address, String secret) {
        return mvc.post().uri("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", address, "password", secret)))
                .exchange();
    }
}
