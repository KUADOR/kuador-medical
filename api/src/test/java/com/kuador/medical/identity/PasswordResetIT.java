package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.notifications.EmailMessage;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Password reset per clinic (ADR-014, spec story 3): always 202, one single-use link per clinic,
 * stored only as a hash, expiring, and revoking the sessions of the account when used.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetIT {

    private static final Pattern LINK_TOKEN = Pattern.compile("/password-reset#t=([A-Za-z0-9_-]+)");

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
        tenantId = api.signUp("Clinica Recupera", email, password);
    }

    @Test
    void knownAndUnknownEmailsGetTheSameAnswer() {
        String unknown = IdentityApi.uniqueEmail("nobody");

        MvcTestResult known = api.requestPasswordReset(email);
        MvcTestResult notKnown = api.requestPasswordReset(unknown);

        assertThat(known).hasStatus(HttpStatus.ACCEPTED);
        assertThat(notKnown).hasStatus(HttpStatus.ACCEPTED);
        assertThat(known.getResponse().getContentAsByteArray()).isEqualTo(notKnown.getResponse().getContentAsByteArray());
        assertThat(mail.sentTo(email)).hasSize(1);
        assertThat(mail.sentTo(unknown)).isEmpty();
    }

    @Test
    void theEmailCarriesAnAppLinkWithTheTokenInTheFragmentStoredOnlyAsAHash() {
        api.requestPasswordReset(email);

        EmailMessage message = mail.sentTo(email).getLast();
        String token = token(message);
        assertThat(message.text()).contains("Clinica Recupera");
        assertThat(jdbc.queryForObject(
                "select count(*) from identity_one_time_token where token_hash = ?", Integer.class, token)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from identity_one_time_token
                where tenant_id = ? and purpose = 'PASSWORD_RESET' and used_at is null""",
                Integer.class, tenantId)).isOne();
    }

    @Test
    void oneLinkPerClinicEachBoundToItsClinic() {
        UUID otherClinic = api.signUp("Clinica Segunda", IdentityApi.uniqueEmail("owner-b"), IdentityApi.randomPassword());
        staff.membership(otherClinic, staff.accountId(email));

        api.requestPasswordReset(email);

        List<EmailMessage> sent = mail.sentTo(email);
        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(EmailMessage::text)
                .anySatisfy(text -> assertThat(text).contains("Clinica Recupera"))
                .anySatisfy(text -> assertThat(text).contains("Clinica Segunda"));
        assertThat(jdbc.queryForList(
                "select tenant_id from identity_one_time_token where user_id = ? and purpose = 'PASSWORD_RESET'",
                UUID.class, staff.accountId(email))).containsExactlyInAnyOrder(tenantId, otherClinic);
    }

    @Test
    void theTokenSetsANewPasswordOnceAndRevokesTheSessions() {
        String refreshToken = api.session(email, password).get("refreshToken").toString();
        api.requestPasswordReset(email);
        String token = token(mail.sentTo(email).getLast());
        String newPassword = IdentityApi.randomPassword();

        assertThat(api.confirmPasswordReset(token, newPassword)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(api.login(email, password)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.login(email, newPassword)).hasStatus(HttpStatus.OK);
        assertThat(api.refresh(refreshToken)).as("sessions are revoked").hasStatus(HttpStatus.UNAUTHORIZED);
        assertOneTimeTokenInvalid(api.confirmPasswordReset(token, IdentityApi.randomPassword()));
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'PASSWORD_RESET_COMPLETED' and tenant_id = ?",
                Integer.class, tenantId)).isOne();
    }

    @Test
    void aWeakPasswordDoesNotConsumeTheToken() {
        api.requestPasswordReset(email);
        String token = token(mail.sentTo(email).getLast());

        assertThat(api.confirmPasswordReset(token, "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("WEAK_PASSWORD"));
        assertThat(api.confirmPasswordReset(token, IdentityApi.randomPassword())).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void expiredUnknownOrSupersededTokensAreRejected() {
        api.requestPasswordReset(email);
        String superseded = token(mail.sentTo(email).getLast());
        api.requestPasswordReset(email);
        String expired = token(mail.sentTo(email).getLast());
        jdbc.update("update identity_one_time_token set expires_at = now() - interval '1 minute' where tenant_id = ?",
                tenantId);

        assertOneTimeTokenInvalid(api.confirmPasswordReset(superseded, IdentityApi.randomPassword()));
        assertOneTimeTokenInvalid(api.confirmPasswordReset(expired, IdentityApi.randomPassword()));
        MvcTestResult unknown = api.confirmPasswordReset("unknown-token-" + UUID.randomUUID(), IdentityApi.randomPassword());
        assertOneTimeTokenInvalid(unknown);
        ContractSchema.userRegistration().assertResponse("/auth/password-reset/confirm", "POST", 400,
                "application/problem+json", api.body(unknown));
    }

    @Test
    void aDisabledAccountGetsNoLink() {
        jdbc.update("update user_account set status = 'DISABLED' where email = ?", email);

        assertThat(api.requestPasswordReset(email)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(mail.sentTo(email)).isEmpty();
    }

    private static String token(EmailMessage message) {
        Matcher matcher = LINK_TOKEN.matcher(message.text());
        assertThat(matcher.find()).as("reset link in the email").isTrue();
        return matcher.group(1);
    }

    private static void assertOneTimeTokenInvalid(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ONE_TIME_TOKEN_INVALID"));
    }
}
