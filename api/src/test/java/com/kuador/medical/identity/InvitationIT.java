package com.kuador.medical.identity;

import static com.kuador.medical.identity.StaffApi.allBranches;
import static com.kuador.medical.identity.StaffApi.id;
import static com.kuador.medical.identity.StaffApi.newStaff;
import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
 * Invitation links (ADR-014, MEDK-16): preview and acceptance with the token in the body, single
 * use, expiry, password policy and storage as a hash only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InvitationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingNotificationSender mail;

    private final ContractSchema contract = ContractSchema.userRegistration();
    private IdentityApi api;
    private StaffApi staffApi;
    private Clinic clinic;
    private String email;
    private UUID user;
    private String token;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staffApi = new StaffApi(mvc, jsonMapper, mail);
        StaffFixture staff = new StaffFixture(jdbc, passwordEncoder);
        clinic = api.clinic("Clinica Invita");
        staff.branch(clinic.tenantId(), "Matriz");
        email = IdentityApi.uniqueEmail("invited");
        user = id(api.body(staffApi.create(clinic.token(),
                newStaff(email, allBranches(staff.role(clinic.tenantId(), "RECEPTION"))))));
        token = staffApi.lastInvitationToken(email);
    }

    @Test
    void emailIsInSpanishAndCarriesTheTokenInTheFragment() {
        assertThat(staffApi.emailsTo(email)).singleElement().satisfies(message -> {
            assertThat(message.subject()).isEqualTo("Invitación a Clinica Invita en Medical Kuador");
            assertThat(message.body())
                    .contains("Hola Jane")
                    .contains("Clinica Invita te invitó")
                    .contains("https://app.example.test/invitation#t=" + token)
                    .contains("un solo uso")
                    .contains("vence");
        });
    }

    @Test
    void previewShowsTheClinicAndAMaskedEmail() {
        MvcTestResult result = staffApi.preview(token);

        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        assertThat(body).containsEntry("clinicName", "Clinica Invita").containsEntry("kind", "STAFF");
        assertThat(body.get("maskedEmail").toString()).startsWith("i***@").endsWith("example.test").doesNotContain("invited-");
        contract.assertResponse("/auth/invitations/preview", "POST", 200, "application/json", body);
    }

    @Test
    void acceptingSetsThePasswordActivatesAndLogsIn() {
        String password = IdentityApi.randomPassword();

        MvcTestResult result = staffApi.accept(token, password);

        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        assertThat(body).containsEntry("status", "AUTHENTICATED").containsEntry("scope", "TENANT");
        assertThat(api.me(body.get("accessToken").toString())).hasStatus(HttpStatus.OK);
        assertThat(api.login(email, password)).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForMap("select status, password_hash from identity_user_account where id = ?", user))
                .satisfies(account -> {
                    assertThat(account.get("status")).isEqualTo("ACTIVE");
                    assertThat(account.get("password_hash").toString()).startsWith("{argon2}").doesNotContain(password);
                });
        assertThat(jdbc.queryForList(
                "select action from audit_event where tenant_id = ? and target_id = ?", String.class, clinic.tenantId(), user))
                .contains("INVITATION_ACCEPTED");
    }

    @Test
    void anInvitationIsSingleUse() {
        assertThat(staffApi.accept(token, IdentityApi.randomPassword())).hasStatus(HttpStatus.OK);

        for (MvcTestResult again : List.of(staffApi.accept(token, IdentityApi.randomPassword()), staffApi.preview(token))) {
            assertThat(again)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ONE_TIME_TOKEN_INVALID"));
        }
    }

    @Test
    void anExpiredInvitationIsRejected() {
        jdbc.update("update identity_one_time_token set expires_at = now() - interval '1 minute' where user_id = ?", user);

        for (MvcTestResult result : List.of(staffApi.preview(token), staffApi.accept(token, IdentityApi.randomPassword()))) {
            assertThat(result)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ONE_TIME_TOKEN_INVALID"));
        }
        assertThat(jdbc.queryForObject("select status from identity_user_account where id = ?", String.class, user))
                .isEqualTo("INVITED");
    }

    @Test
    void unknownTokensAreRejectedTheSameWay() {
        assertThat(staffApi.preview("unknown-token-" + UUID.randomUUID()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ONE_TIME_TOKEN_INVALID"));
    }

    @Test
    void aWeakPasswordDoesNotConsumeTheInvitation() {
        assertThat(staffApi.accept(token, "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("WEAK_PASSWORD"));

        assertThat(staffApi.accept(token, IdentityApi.randomPassword())).hasStatus(HttpStatus.OK);
    }

    @Test
    void aDisabledUserCannotAcceptItsInvitation() {
        assertThat(staffApi.action(clinic.token(), user, "disable")).hasStatus(HttpStatus.OK);

        assertThat(staffApi.accept(token, IdentityApi.randomPassword())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select status from identity_user_account where id = ?", String.class, user))
                .isEqualTo("DISABLED");
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() {
        List<String> stored = jdbc.queryForList(
                "select token_hash from identity_one_time_token where user_id = ? and purpose = 'INVITATION'", String.class, user);

        assertThat(stored).singleElement().isEqualTo(sha256(token)).isNotEqualTo(token);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
