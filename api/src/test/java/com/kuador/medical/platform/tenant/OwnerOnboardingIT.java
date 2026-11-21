package com.kuador.medical.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.TotpCodes;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * OWNER invitation, terms acceptance and mandatory TOTP second factor (MEDK-67; MEDK-49 CA 4
 * invitation, 5, 6 and 7; ADR-014, ADR-019; plan D7): the invitation is created with the clinic,
 * sent by email with the token in the fragment and stored only as a hash; used, expired and
 * replaced links are rejected; the OWNER does not operate until it enrolls TOTP and every login
 * asks for it; a SUPER_ADMIN resets it with an audited reason; and nothing crosses clinics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class OwnerOnboardingIT {

    private static final String TENANTS = "/api/v1/platform/tenants";
    private static final String TRIAL_PLAN_ID = "6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d";
    private static final String PREVIEW = "/api/v1/public/owner-invitations/preview";
    private static final String ACCEPT = "/api/v1/public/owner-invitations/accept";
    private static final String ENROLL = "/api/v1/auth/two-factor/enrollment";
    private static final String CONFIRM = "/api/v1/auth/two-factor/enrollment/confirm";
    private static final String VERIFY = "/api/v1/auth/two-factor/verify";
    private static final Pattern LINK = Pattern.compile("https://app\\.example\\.test/owner-invitation#t=([A-Za-z0-9_-]+)");

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

    private final ContractSchema contract = ContractSchema.platform();
    private IdentityApi api;
    private Admin admin;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
    }

    // ------------------------------------------------------------------ invitation (CA 4, CA 5)

    @Test
    void onboardingInvitesTheOwnerByEmailWithAOneTimeLinkStoredOnlyAsAHash() {
        Onboarded clinic = onboard();

        EmailMessage email = single(mail.awaitSentTo(clinic.ownerEmail(), 1));
        assertThat(email.subject()).isEqualTo("Activa tu cuenta de " + clinic.name() + " en Medical Kuador");
        assertThat(email.body()).contains("Hola Ana Prueba").contains("un solo uso").contains("vence");
        String token = clinic.token();
        assertThat(jdbc.queryForObject("select count(*) from identity_owner_invitation where token_hash = ?",
                Integer.class, sha256(token))).as("only the hash is stored").isOne();
        assertThat(jdbc.queryForObject("select count(*) from identity_owner_invitation where token_hash = ?",
                Integer.class, token)).isZero();
        Map<String, Object> stored = jdbc.queryForMap("""
                select tenant_id, round(extract(epoch from expires_at - created_at)) as ttl
                from identity_owner_invitation where token_hash = ?""",
                sha256(token));
        assertThat(stored.get("tenant_id")).hasToString(clinic.id());
        assertThat(((Number) stored.get("ttl")).longValue())
                .as("72 hours by default (OWNER_INVITATION_TTL_HOURS)")
                .isEqualTo(72L * 3600);

        Map<String, Object> tenant = api.body(api.get(TENANTS + "/" + clinic.id(), admin.token()));
        contract.assertResponse("/platform/tenants/{tenantId}", "GET", 200, "application/json", tenant);
        Map<String, Object> owner = cast(tenant.get("owner"));
        assertThat(owner).containsEntry("twoFactorEnabled", false);
        assertThat(cast(owner.get("invitation"))).containsEntry("status", "PENDING");
    }

    @Test
    void previewShowsTheClinicTheMaskedEmailAndTheVersionsToAccept() {
        Onboarded clinic = onboard();

        MvcTestResult preview = api.publicPost(PREVIEW, Map.of("token", clinic.token()));

        assertThat(preview).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(preview);
        contract.assertResponse("/public/owner-invitations/preview", "POST", 200, "application/json", body);
        assertThat(body).containsEntry("clinicName", clinic.name()).containsEntry("ownerName", "Ana Prueba")
                .containsKeys("termsVersion", "dataPolicyVersion", "expiresAt");
        assertThat(body.get("maskedEmail").toString()).startsWith("a***@").doesNotContain(clinic.ownerEmail());
    }

    @Test
    void acceptingSetsThePasswordRecordsTheTermsAndConsumesTheLink() {
        Onboarded clinic = onboard();
        Map<String, Object> versions = api.body(api.publicPost(PREVIEW, Map.of("token", clinic.token())));
        String password = IdentityApi.randomPassword();

        MvcTestResult accepted = api.publicPost(ACCEPT, accept(clinic.token(), password, versions));

        assertThat(accepted).hasStatus(HttpStatus.OK);
        Map<String, Object> session = api.body(accepted);
        contract.assertResponse("/public/owner-invitations/accept", "POST", 200, "application/json", session);
        assertThat(session).containsEntry("status", "AUTHENTICATED").containsEntry("scope", "TENANT");
        UUID ownerId = UUID.fromString(api.claims(session.get("accessToken").toString()).get("sub").toString());
        Map<String, Object> terms = jdbc.queryForMap(
                "select tenant_id, terms_version, data_policy_version, accepted_at from identity_terms_acceptance where user_id = ?",
                ownerId);
        assertThat(terms.get("tenant_id")).hasToString(clinic.id());
        assertThat(terms).containsEntry("terms_version", versions.get("termsVersion"))
                .containsEntry("data_policy_version", versions.get("dataPolicyVersion"));
        assertThat(terms.get("accepted_at")).isNotNull();
        assertThat(audited("OWNER_INVITATION_ACCEPTED", clinic.id())).isOne();
        assertThat(audited("TERMS_ACCEPTED", clinic.id())).isOne();

        assertGone(api.publicPost(PREVIEW, Map.of("token", clinic.token())), "/public/owner-invitations/preview");
        assertGone(api.publicPost(ACCEPT, accept(clinic.token(), password, versions)), "/public/owner-invitations/accept");
        Map<String, Object> owner = cast(api.body(api.get(TENANTS + "/" + clinic.id(), admin.token())).get("owner"));
        assertThat(cast(owner.get("invitation"))).containsEntry("status", "ACCEPTED");
    }

    @Test
    void expiredAndUnknownLinksAreGone() {
        Onboarded clinic = onboard();
        jdbc.update("update identity_owner_invitation set created_at = now() - interval '73 hours', expires_at = now() - interval '1 minute' where token_hash = ?",
                sha256(clinic.token()));

        assertGone(api.publicPost(PREVIEW, Map.of("token", clinic.token())), "/public/owner-invitations/preview");
        assertGone(api.publicPost(ACCEPT, accept(clinic.token(), IdentityApi.randomPassword(), versions())),
                "/public/owner-invitations/accept");
        assertGone(api.publicPost(PREVIEW, Map.of("token", "unknown-token-" + UUID.randomUUID())),
                "/public/owner-invitations/preview");
        Map<String, Object> owner = cast(api.body(api.get(TENANTS + "/" + clinic.id(), admin.token())).get("owner"));
        assertThat(cast(owner.get("invitation"))).containsEntry("status", "EXPIRED");
    }

    @Test
    void resendingRevokesThePreviousLinkAndCanCorrectTheEmail() {
        Onboarded clinic = onboard();
        String corrected = IdentityApi.uniqueEmail("owner-fixed");

        MvcTestResult resent = api.post(TENANTS + "/" + clinic.id() + "/owner-invitations", admin.token(),
                Map.of("email", corrected));

        assertThat(resent).hasStatus(HttpStatus.CREATED);
        Map<String, Object> invitation = api.body(resent);
        contract.assertResponse("/platform/tenants/{tenantId}/owner-invitations", "POST", 201, "application/json", invitation);
        assertThat(invitation).containsEntry("status", "PENDING");
        assertThat(invitation.get("maskedEmail").toString()).startsWith("o***@");
        assertGone(api.publicPost(PREVIEW, Map.of("token", clinic.token())), "/public/owner-invitations/preview");
        String newToken = token(single(mail.awaitSentTo(corrected, 1)));
        assertThat(api.publicPost(PREVIEW, Map.of("token", newToken))).hasStatus(HttpStatus.OK);
        assertThat(audited("OWNER_INVITATION_SENT", clinic.id())).isEqualTo(2);

        Map<String, Object> tenant = api.body(api.get(TENANTS + "/" + clinic.id(), admin.token()));
        assertThat(cast(tenant.get("owner")).get("maskedEmail").toString()).startsWith("o***@");

        String password = IdentityApi.randomPassword();
        assertThat(api.publicPost(ACCEPT, accept(newToken, password, versions()))).hasStatus(HttpStatus.OK);
        assertThat(api.login(corrected, password)).as("the corrected email logs in").hasStatus(HttpStatus.OK);

        MvcTestResult again = api.post(TENANTS + "/" + clinic.id() + "/owner-invitations", admin.token(), Map.of());
        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.body(again)).containsEntry("code", "ACCOUNT_ALREADY_ACTIVE");
    }

    @Test
    void aWeakPasswordOrOutdatedVersionsDoNotConsumeTheLink() {
        Onboarded clinic = onboard();

        MvcTestResult weak = api.publicPost(ACCEPT, accept(clinic.token(), "short", versions()));
        assertThat(weak).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(weak)).containsEntry("code", "WEAK_PASSWORD");

        Map<String, Object> outdated = new LinkedHashMap<>(accept(clinic.token(), IdentityApi.randomPassword(), versions()));
        outdated.put("termsVersion", "1999-01");
        MvcTestResult old = api.publicPost(ACCEPT, outdated);
        assertThat(old).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(old)).containsEntry("code", "VALIDATION");

        Map<String, Object> declined = new LinkedHashMap<>(accept(clinic.token(), IdentityApi.randomPassword(), versions()));
        declined.put("acceptDataPolicy", false);
        assertThat(api.publicPost(ACCEPT, declined)).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(api.publicPost(PREVIEW, Map.of("token", clinic.token()))).as("still valid").hasStatus(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ second factor (CA 6)

    @Test
    void theOwnerDoesNotOperateUntilItEnrollsTheSecondFactor(CapturedOutput output) {
        Onboarded clinic = onboard();
        Map<String, Object> session = acceptInvitation(clinic, IdentityApi.randomPassword());
        String pending = session.get("accessToken").toString();

        assertTwoFactorRequired(api.me(pending), "ENROLLMENT_REQUIRED");
        assertTwoFactorRequired(api.get("/api/v1/roles", pending), "ENROLLMENT_REQUIRED");
        assertTwoFactorRequired(api.post(VERIFY, pending, Map.of("code", "123456")), "ENROLLMENT_REQUIRED");

        MvcTestResult started = api.post(ENROLL, pending, Map.of());
        assertThat(started).hasStatus(HttpStatus.OK);
        Map<String, Object> enrollment = api.body(started);
        contract.assertResponse("/auth/two-factor/enrollment", "POST", 200, "application/json", enrollment);
        String secret = enrollment.get("secret").toString();
        assertThat(enrollment).containsEntry("issuer", "Medical Kuador").containsEntry("digits", 6).containsEntry("period", 30);
        assertThat(enrollment.get("otpauthUri").toString()).startsWith("otpauth://totp/").contains("secret=" + secret)
                .doesNotContain(clinic.ownerEmail());
        assertThat(jdbc.queryForObject("select count(*) from identity_two_factor where secret_encrypted like ?",
                Integer.class, "%" + secret + "%")).as("stored encrypted").isZero();

        MvcTestResult wrong = api.post(CONFIRM, pending, Map.of("code", otherCode(secret)));
        assertThat(wrong).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(wrong)).containsEntry("code", "TWO_FACTOR_CODE_INVALID");

        MvcTestResult confirmed = api.post(CONFIRM, pending, Map.of("code", TotpCodes.currentCode(secret)));
        assertThat(confirmed).hasStatus(HttpStatus.OK);
        Map<String, Object> verified = api.body(confirmed);
        contract.assertResponse("/auth/two-factor/enrollment/confirm", "POST", 200, "application/json", verified);
        String access = verified.get("accessToken").toString();
        assertThat(api.me(access)).hasStatus(HttpStatus.OK);
        assertThat(api.post(ENROLL, access, Map.of())).hasStatus(HttpStatus.CONFLICT);
        assertThat(audited("TWO_FACTOR_ENABLED", clinic.id())).isOne();

        MvcTestResult refreshed = api.refresh(verified.get("refreshToken").toString());
        assertThat(refreshed).hasStatus(HttpStatus.OK);
        assertThat(api.me(api.body(refreshed).get("accessToken").toString()))
                .as("a refresh keeps the verified second factor").hasStatus(HttpStatus.OK);

        Map<String, Object> owner = cast(api.body(api.get(TENANTS + "/" + clinic.id(), admin.token())).get("owner"));
        assertThat(owner).containsEntry("twoFactorEnabled", true);
        assertThat(output.getAll()).doesNotContain(secret).doesNotContain(clinic.token());
    }

    @Test
    void everyLoginOfTheOwnerAsksForTheCodeAndACodeIsUsedOnce() {
        Onboarded clinic = onboard();
        String password = IdentityApi.randomPassword();
        String secret = enroll(acceptInvitation(clinic, password));

        Map<String, Object> login = api.rawSession(clinic.ownerEmail(), password);
        String pending = login.get("accessToken").toString();
        assertTwoFactorRequired(api.me(pending), "VERIFICATION_REQUIRED");
        MvcTestResult refreshed = api.refresh(login.get("refreshToken").toString());
        assertThat(refreshed).hasStatus(HttpStatus.OK);
        assertTwoFactorRequired(api.me(api.body(refreshed).get("accessToken").toString()), "VERIFICATION_REQUIRED");

        MvcTestResult wrong = api.post(VERIFY, pending, Map.of("code", otherCode(secret)));
        assertThat(wrong).hasStatus(HttpStatus.BAD_REQUEST);
        contract.assertResponse("/auth/two-factor/verify", "POST", 400, "application/problem+json", api.body(wrong));
        assertThat(api.body(wrong)).containsEntry("code", "TWO_FACTOR_CODE_INVALID");
        MvcTestResult malformed = api.post(VERIFY, pending, Map.of("code", "12ab56"));
        assertThat(malformed).hasStatus(HttpStatus.BAD_REQUEST);

        String code = TotpCodes.code(secret, TotpCodes.currentStep() + 1);
        MvcTestResult verified = api.post(VERIFY, pending, Map.of("code", code));
        assertThat(verified).hasStatus(HttpStatus.OK);
        contract.assertResponse("/auth/two-factor/verify", "POST", 200, "application/json", api.body(verified));
        assertThat(api.me(api.body(verified).get("accessToken").toString())).hasStatus(HttpStatus.OK);

        String second = api.rawSession(clinic.ownerEmail(), password).get("accessToken").toString();
        MvcTestResult replayed = api.post(VERIFY, second, Map.of("code", code));
        assertThat(replayed).as("a code already used is rejected").hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(replayed)).containsEntry("code", "TWO_FACTOR_CODE_INVALID");
    }

    @Test
    void repeatedWrongCodesAreBlockedForAWhile() {
        Onboarded clinic = onboard();
        String password = IdentityApi.randomPassword();
        String secret = enroll(acceptInvitation(clinic, password));
        String pending = api.rawSession(clinic.ownerEmail(), password).get("accessToken").toString();

        MvcTestResult last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            last = api.post(VERIFY, pending, Map.of("code", otherCode(secret)));
        }

        assertThat(last).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(api.body(last)).containsEntry("code", "TOO_MANY_ATTEMPTS");
        assertThat(api.post(VERIFY, pending, Map.of("code", TotpCodes.currentCode(secret))))
                .as("even the right code waits for the lock").hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void anAccountWithoutOwnerRoleAndThePlatformAdminAreNotAskedForTheSecondFactor() {
        assertThat(api.get("/api/v1/platform/plans", admin.token())).hasStatus(HttpStatus.OK);
        assertThat(api.me(admin.token())).hasStatus(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ reset by SUPER_ADMIN (D7)

    @Test
    void aSuperAdminResetsTheSecondFactorWithAnAuditedReasonAndTheOwnerEnrollsAgain() {
        Onboarded clinic = onboard();
        String password = IdentityApi.randomPassword();
        Map<String, Object> session = acceptInvitation(clinic, password);
        enroll(session);
        String reset = TENANTS + "/" + clinic.id() + "/owner/two-factor-reset";

        MvcTestResult noReason = api.post(reset, admin.token(), Map.of("reason", " "));
        assertThat(noReason).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(noReason)).containsEntry("code", "VALIDATION");

        assertThat(api.post(reset, admin.token(), Map.of("reason", "Perdió el teléfono; identidad verificada.")))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(audited("TWO_FACTOR_RESET", clinic.id())).isOne();
        assertThat(jdbc.queryForObject("""
                select reset_reason from identity_two_factor where tenant_id = ?::uuid and reset_at is not null""",
                String.class, clinic.id())).isEqualTo("Perdió el teléfono; identidad verificada.");
        assertThat(mail.awaitSentTo(clinic.ownerEmail(), 2)).last()
                .satisfies(email -> assertThat(email.subject()).contains("verificación en dos pasos"));
        String pending = api.rawSession(clinic.ownerEmail(), password).get("accessToken").toString();
        assertTwoFactorRequired(api.me(pending), "ENROLLMENT_REQUIRED");
        assertTwoFactorRequired(api.me(session.get("accessToken").toString()), "ENROLLMENT_REQUIRED");
        Map<String, Object> owner = cast(api.body(api.get(TENANTS + "/" + clinic.id(), admin.token())).get("owner"));
        assertThat(owner).containsEntry("twoFactorEnabled", false);
    }

    // ------------------------------------------------------------------ permissions and isolation

    @Test
    void clinicTokensCannotResendNorResetEvenOnTheirOwnClinic() {
        Onboarded clinic = onboard();
        String password = IdentityApi.randomPassword();
        String ownerToken = verifiedToken(clinic, password);

        assertThat(api.post(TENANTS + "/" + clinic.id() + "/owner-invitations", ownerToken, Map.of()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.post(TENANTS + "/" + clinic.id() + "/owner/two-factor-reset", ownerToken,
                Map.of("reason", "Intento indebido"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.post(TENANTS + "/" + UUID.randomUUID() + "/owner-invitations", admin.token(), Map.of()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void invitationsAndSecondFactorsStayInsideTheirClinic() {
        Onboarded clinicA = onboard();
        Onboarded clinicB = onboard();
        String passwordA = IdentityApi.randomPassword();
        String passwordB = IdentityApi.randomPassword();
        String tokenA = verifiedToken(clinicA, passwordA);
        String tokenB = verifiedToken(clinicB, passwordB);

        assertThat(api.login(clinicA.ownerEmail(), passwordB)).as("B's password does not open A")
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(distinct tenant_id) from identity_two_factor where tenant_id in (?::uuid, ?::uuid)",
                Integer.class, clinicA.id(), clinicB.id())).isEqualTo(2);

        assertThat(api.post(TENANTS + "/" + clinicB.id() + "/owner/two-factor-reset", admin.token(),
                Map.of("reason", "Restablecimiento solo de B"))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(api.me(tokenA)).as("A keeps its second factor").hasStatus(HttpStatus.OK);
        assertTwoFactorRequired(api.me(tokenB), "ENROLLMENT_REQUIRED");
        assertThat(cast(cast(api.body(api.get(TENANTS + "/" + clinicA.id(), admin.token()))).get("owner")))
                .containsEntry("twoFactorEnabled", true);
        Map<String, Object> roleMe = api.body(api.me(tokenA));
        assertThat(roleMe.toString()).doesNotContain(clinicB.id());
    }

    // ------------------------------------------------------------------ helpers

    record Onboarded(String id, String name, String ownerEmail, String token) {}

    private Onboarded onboard() {
        Map<String, Object> request = PlatformTenantsIT.tenantRequest(TRIAL_PLAN_ID);
        String ownerEmail = IdentityApi.uniqueEmail("ana");
        cast(request.get("owner")).put("email", ownerEmail);
        MvcTestResult created = api.post(TENANTS, admin.token(), request);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> tenant = api.body(created);
        String token = token(single(mail.awaitSentTo(ownerEmail, 1)));
        return new Onboarded(tenant.get("id").toString(), request.get("tradeName").toString(), ownerEmail, token);
    }

    private Map<String, Object> versions() {
        // Versions are configuration; read them from any valid preview.
        Onboarded other = onboard();
        return api.body(api.publicPost(PREVIEW, Map.of("token", other.token())));
    }

    private static Map<String, Object> accept(String token, String password, Map<String, Object> versions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("password", password);
        body.put("acceptTerms", true);
        body.put("termsVersion", versions.get("termsVersion"));
        body.put("acceptDataPolicy", true);
        body.put("dataPolicyVersion", versions.get("dataPolicyVersion"));
        return body;
    }

    private Map<String, Object> acceptInvitation(Onboarded clinic, String password) {
        Map<String, Object> versions = api.body(api.publicPost(PREVIEW, Map.of("token", clinic.token())));
        MvcTestResult accepted = api.publicPost(ACCEPT, accept(clinic.token(), password, versions));
        assertThat(accepted).hasStatus(HttpStatus.OK);
        return api.body(accepted);
    }

    /** Enrolls TOTP with the pending session and returns the Base32 secret. */
    private String enroll(Map<String, Object> session) {
        String pending = session.get("accessToken").toString();
        String secret = api.body(api.post(ENROLL, pending, Map.of())).get("secret").toString();
        assertThat(api.post(CONFIRM, pending, Map.of("code", TotpCodes.currentCode(secret))))
                .hasStatus(HttpStatus.OK);
        return secret;
    }

    private String verifiedToken(Onboarded clinic, String password) {
        Map<String, Object> session = acceptInvitation(clinic, password);
        String pending = session.get("accessToken").toString();
        String secret = api.body(api.post(ENROLL, pending, Map.of())).get("secret").toString();
        MvcTestResult confirmed = api.post(CONFIRM, pending, Map.of("code", TotpCodes.currentCode(secret)));
        assertThat(confirmed).hasStatus(HttpStatus.OK);
        return api.body(confirmed).get("accessToken").toString();
    }

    private void assertTwoFactorRequired(MvcTestResult result, String step) {
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = api.body(result);
        assertThat(body).containsEntry("code", "TWO_FACTOR_REQUIRED").containsEntry("twoFactor", step);
    }

    private void assertGone(MvcTestResult result, String path) {
        assertThat(result).hasStatus(HttpStatus.GONE);
        Map<String, Object> body = api.body(result);
        contract.assertResponse(path, "POST", 410, "application/problem+json", body);
        assertThat(body).containsEntry("code", "INVITATION_INVALID_OR_EXPIRED");
    }

    private int audited(String action, String tenantId) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and tenant_id = ?::uuid",
                Integer.class, action, tenantId);
    }

    /** A valid-looking code that is none of the codes of the window. */
    private static String otherCode(String secret) {
        long step = TotpCodes.currentStep();
        List<String> window = List.of(TotpCodes.code(secret, step - 1), TotpCodes.code(secret, step),
                TotpCodes.code(secret, step + 1), TotpCodes.code(secret, step + 2));
        for (int candidate = 0; ; candidate++) {
            String code = "%06d".formatted(candidate);
            if (!window.contains(code)) {
                return code;
            }
        }
    }

    private static String token(EmailMessage email) {
        Matcher matcher = LINK.matcher(email.body());
        assertThat(matcher.find()).as("owner invitation link in the email").isTrue();
        return matcher.group(1);
    }

    private static <T> T single(List<T> items) {
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
