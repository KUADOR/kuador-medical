package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.List;
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
 * Login across clinics (ADR-012, spec story 2): the clinics where the credentials match are
 * listed, the chosen one binds the session, and the pre-auth token cannot be reused or abused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MultiClinicLoginIT {

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
    private UUID clinicA;
    private UUID clinicB;
    private UUID otherClinic;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staff = new StaffFixture(jdbc, passwordEncoder);
        email = IdentityApi.uniqueEmail("multi");
        password = IdentityApi.randomPassword();
        clinicA = api.signUp("Clinica Uno", email, password);
        clinicB = api.signUp("Clinica Dos", IdentityApi.uniqueEmail("owner-b"), IdentityApi.randomPassword());
        otherClinic = api.signUp("Clinica Ajena", IdentityApi.uniqueEmail("owner-c"), IdentityApi.randomPassword());
        staff.membership(clinicB, staff.accountId(email));
    }

    @Test
    void credentialsMatchingTwoClinicsListOnlyThoseClinics() {
        MvcTestResult result = api.login(email, password);

        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        assertThat(body).containsEntry("status", "CLINIC_SELECTION_REQUIRED").containsEntry("preAuthExpiresIn", 300);
        assertThat(clinicIds(body)).containsExactlyInAnyOrder(clinicA.toString(), clinicB.toString());
        ContractSchema.userRegistration().assertResponse("/auth/login", "POST", 200, "application/json", body);
    }

    @Test
    void choosingAClinicBindsTheSessionToIt() {
        String preAuth = api.body(api.login(email, password)).get("preAuthToken").toString();

        MvcTestResult chosen = api.selectClinic(preAuth, clinicB);

        assertThat(chosen).hasStatus(HttpStatus.OK);
        Map<String, Object> session = api.body(chosen);
        assertThat(session).containsEntry("status", "AUTHENTICATED")
                .containsEntry("clinic", Map.of("id", clinicB.toString(), "name", "Clinica Dos"));
        assertThat(api.claims(session.get("accessToken").toString())).containsEntry("tid", clinicB.toString());
        assertThat(api.body(api.me(session.get("accessToken").toString())).get("clinic"))
                .isEqualTo(Map.of("id", clinicB.toString(), "name", "Clinica Dos"));
        ContractSchema.userRegistration().assertResponse("/auth/login/clinic", "POST", 200, "application/json", session);
    }

    @Test
    void aClinicThatWasNotListedIsRejected() {
        String preAuth = api.body(api.login(email, password)).get("preAuthToken").toString();

        assertInvalidSessionToken(api.selectClinic(preAuth, otherClinic));
    }

    @Test
    void thePreAuthTokenIsSingleUse() {
        String preAuth = api.body(api.login(email, password)).get("preAuthToken").toString();
        assertThat(api.selectClinic(preAuth, clinicA)).hasStatus(HttpStatus.OK);

        assertInvalidSessionToken(api.selectClinic(preAuth, clinicA));
        assertInvalidSessionToken(api.selectClinic(preAuth, clinicB));
    }

    @Test
    void thePreAuthTokenIsNotAnAccessToken() {
        String preAuth = api.body(api.login(email, password)).get("preAuthToken").toString();

        assertThat(api.me(preAuth)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertInvalidSessionToken(api.selectClinic("not-a-token", clinicA));
    }

    @Test
    void aDisabledMembershipIsNotListed() {
        jdbc.update("update tenant_membership set status = 'DISABLED' where tenant_id = ? and user_account_id = ?",
                clinicB, staff.accountId(email));

        Map<String, Object> body = api.body(api.login(email, password));

        assertThat(body).containsEntry("status", "AUTHENTICATED")
                .containsEntry("clinic", Map.of("id", clinicA.toString(), "name", "Clinica Uno"));
    }

    private void assertInvalidSessionToken(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INVALID_SESSION_TOKEN"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> clinicIds(Map<String, Object> body) {
        return ((List<Map<String, Object>>) body.get("clinics")).stream().map(clinic -> clinic.get("id").toString()).toList();
    }
}
