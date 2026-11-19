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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rotating refresh tokens stored only as hashes (ADR-013): rotation, reuse detection, logout,
 * disabled accounts and isolation between clinics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private IdentityApi api;
    private StaffFixture staff;
    private String email;
    private String password;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staff = new StaffFixture(jdbc, passwordEncoder);
        email = IdentityApi.uniqueEmail("owner");
        password = IdentityApi.randomPassword();
        tenantId = api.signUp("Clinica Refresh", email, password);
    }

    @Test
    void loginIssuesARefreshTokenStoredOnlyAsAHash() {
        Map<String, Object> session = api.session(email, password);
        String refreshToken = session.get("refreshToken").toString();

        assertThat(session).containsEntry("refreshExpiresIn", 604800).containsEntry("expiresIn", 900);
        assertThat(jdbc.queryForObject(
                "select count(*) from identity_refresh_token where token_hash = ? or token_hash = ?",
                Integer.class, refreshToken, refreshToken.toLowerCase())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from identity_refresh_token where tenant_id = ? and user_id = ?",
                Integer.class, tenantId, staff.accountId(tenantId, email))).isOne();
    }

    @Test
    void refreshRotatesTheTokenForTheSameClinic() {
        String first = api.session(email, password).get("refreshToken").toString();

        MvcTestResult refreshed = api.refresh(first);

        assertThat(refreshed).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(refreshed);
        assertThat(body).containsEntry("status", "AUTHENTICATED")
                .containsEntry("clinic", Map.of("id", tenantId.toString(), "name", "Clinica Refresh"));
        assertThat(body.get("refreshToken")).isNotEqualTo(first);
        assertThat(api.me(body.get("accessToken").toString())).hasStatus(HttpStatus.OK);
        ContractSchema.userRegistration().assertResponse("/auth/refresh", "POST", 200, "application/json", body);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSession() {
        String first = api.session(email, password).get("refreshToken").toString();
        String second = api.body(api.refresh(first)).get("refreshToken").toString();

        assertInvalidSessionToken(api.refresh(first));
        assertInvalidSessionToken(api.refresh(second));
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'REFRESH_TOKEN_REUSED' and tenant_id = ?",
                Integer.class, tenantId)).isOne();
    }

    @Test
    void logoutRevokesTheRefreshTokenAndAlwaysAnswers204() {
        String refreshToken = api.session(email, password).get("refreshToken").toString();

        assertThat(api.logout(refreshToken)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.logout(refreshToken)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.logout("unknown-token")).hasStatus(HttpStatus.NO_CONTENT);
        assertInvalidSessionToken(api.refresh(refreshToken));
    }

    @Test
    void unknownExpiredOrDisabledRefreshTokensAreRejected() {
        String expired = api.session(email, password).get("refreshToken").toString();
        jdbc.update("update identity_refresh_token set expires_at = now() - interval '1 minute' where tenant_id = ?", tenantId);
        String disabled = api.session(email, password).get("refreshToken").toString();
        staff.disable(tenantId, email);

        assertInvalidSessionToken(api.refresh("unknown-token"));
        assertInvalidSessionToken(api.refresh(expired));
        assertInvalidSessionToken(api.refresh(disabled));
        ContractSchema.userRegistration().assertResponse("/auth/refresh", "POST", 401, "application/problem+json",
                api.body(api.refresh("unknown-token")));
    }

    @Test
    void aRefreshTokenOnlyRenewsTheSessionOfItsOwnClinic() {
        String otherEmail = IdentityApi.uniqueEmail("owner-b");
        String otherPassword = IdentityApi.randomPassword();
        UUID otherTenant = api.signUp("Clinica Otra", otherEmail, otherPassword);
        String refreshA = api.session(email, password).get("refreshToken").toString();
        String refreshB = api.session(otherEmail, otherPassword).get("refreshToken").toString();

        Map<String, Object> renewedA = api.body(api.refresh(refreshA));
        Map<String, Object> renewedB = api.body(api.refresh(refreshB));

        assertThat(api.claims(renewedA.get("accessToken").toString())).containsEntry("tid", tenantId.toString());
        assertThat(api.claims(renewedB.get("accessToken").toString())).containsEntry("tid", otherTenant.toString());
    }

    private void assertInvalidSessionToken(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INVALID_SESSION_TOKEN"));
    }
}
