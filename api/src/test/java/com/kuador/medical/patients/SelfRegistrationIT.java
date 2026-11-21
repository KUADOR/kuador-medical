package com.kuador.medical.patients;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * Optional patient self-registration (MEDK-17): disabled by default (404), enabled per clinic,
 * the account becomes active only after the email is verified, duplicates are not revealed and
 * minors cannot self-register.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SelfRegistrationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingNotificationSender mail;

    private final ContractSchema contract = ContractSchema.userRegistration();
    private IdentityApi api;
    private PatientApi patients;
    private Clinic clinic;
    private String publicCode;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        patients = new PatientApi(mvc, jsonMapper, mail);
        clinic = api.clinic("Clinica Autorregistro");
        Map<String, Object> settings = api.body(patients.settings(clinic.token()));
        contract.assertResponse("/patient-registration-settings", "GET", 200, "application/json", settings);
        assertThat(settings).containsEntry("selfRegistrationEnabled", false);
        publicCode = settings.get("publicCode").toString();
        assertThat(publicCode).matches("^[a-z0-9-]{3,60}$");
    }

    @Test
    void disabledSelfRegistrationRespondsNotFound() {
        Map<String, Object> request = PatientApi.selfRegistration(IdentityApi.uniqueEmail("self"), IdentityApi.randomPassword());

        assertThat(patients.selfRegister(publicCode, request, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("NOT_FOUND"));
        assertThat(patients.selfRegister("unknown-clinic-code", request, null)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(countPatients()).isZero();
    }

    @Test
    void aSuspendedClinicDoesNotAcceptSelfRegistrations() {
        enable();
        // MEDK-69: the clinic is suspended (read-only) on the seeded trial plan, which includes PATIENTS.
        jdbc.update("""
                insert into subscription (id, tenant_id, plan_id, billing_period, status, suspended_at)
                values (?, ?, '6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d', 'MONTHLY', 'SUSPENDED', now())""",
                java.util.UUID.randomUUID(), clinic.tenantId());
        Map<String, Object> request = PatientApi.selfRegistration(IdentityApi.uniqueEmail("self"), IdentityApi.randomPassword());

        assertThat(patients.selfRegister(publicCode, request, null))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("TENANT_READ_ONLY"));
        assertThat(countPatients()).isZero();
    }

    @Test
    void theAccountIsActiveOnlyAfterTheEmailIsVerified() {
        enable();
        String email = IdentityApi.uniqueEmail("self");
        String password = IdentityApi.randomPassword();
        Map<String, Object> request = PatientApi.selfRegistration(email, password);

        assertThat(patients.selfRegister(publicCode, request, null)).hasStatus(HttpStatus.ACCEPTED);

        Map<String, Object> patient = jdbc.queryForMap(
                "select id, origin, account_ref from patients_patient where tenant_id = ? and document_number = ?",
                clinic.tenantId(), request.get("documentNumber"));
        assertThat(patient).containsEntry("origin", "SELF");
        assertThat(jdbc.queryForObject("select status from identity_user_account where id = ?", String.class,
                patient.get("account_ref"))).isEqualTo("PENDING_EMAIL_VERIFICATION");
        assertThat(api.body(patients.get(clinic.token(), patient.get("id"))))
                .containsEntry("accountStatus", "PENDING_EMAIL_VERIFICATION")
                .containsEntry("origin", "SELF");
        assertThat(jdbc.queryForObject("select channel from patients_consent where patient_id = ?", String.class,
                patient.get("id"))).isEqualTo("SELF");

        // Before the verification the credentials do not work.
        assertThat(api.login(email, password))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INVALID_CREDENTIALS"));

        String token = patients.lastVerificationToken(email);
        assertThat(patients.confirmEmail(token)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.body(patients.get(clinic.token(), patient.get("id")))).containsEntry("accountStatus", "ACTIVE");
        assertThat(api.login(email, password)).hasStatus(HttpStatus.OK);

        // The token is single-use.
        assertThat(patients.confirmEmail(token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ONE_TIME_TOKEN_INVALID"));
        assertThat(jdbc.queryForList("select action from audit_event where tenant_id = ? and target_id in (?, ?)",
                String.class, clinic.tenantId(), patient.get("id"), patient.get("account_ref")))
                .contains("PATIENT_SELF_REGISTERED", "EMAIL_VERIFICATION_SENT", "EMAIL_VERIFIED");
    }

    @Test
    void anUnknownVerificationTokenIsRejected() {
        assertThat(patients.confirmEmail("an-unknown-token-of-enough-length"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ONE_TIME_TOKEN_INVALID"));
    }

    @Test
    void duplicatesAreNotRevealed() {
        enable();
        String email = IdentityApi.uniqueEmail("self");
        Map<String, Object> request = PatientApi.selfRegistration(email, IdentityApi.randomPassword());
        assertThat(patients.selfRegister(publicCode, request, null)).hasStatus(HttpStatus.ACCEPTED);

        Map<String, Object> sameDocument = PatientApi.selfRegistration(IdentityApi.uniqueEmail("other"), IdentityApi.randomPassword());
        sameDocument.put("documentNumber", request.get("documentNumber"));
        assertThat(patients.selfRegister(publicCode, sameDocument, null)).hasStatus(HttpStatus.ACCEPTED);

        Map<String, Object> sameEmail = PatientApi.selfRegistration(email, IdentityApi.randomPassword());
        assertThat(patients.selfRegister(publicCode, sameEmail, null)).hasStatus(HttpStatus.ACCEPTED);

        assertThat(countPatients()).isEqualTo(1);
        // The person is told by email instead of by the response.
        assertThat(mail.awaitSentTo(sameDocument.get("email").toString(), 1)).hasSize(1);
        assertThat(mail.awaitSentTo(email, 2)).hasSize(2);
    }

    @Test
    void minorsCannotSelfRegister() {
        enable();
        Map<String, Object> request = PatientApi.selfRegistration(IdentityApi.uniqueEmail("self"), IdentityApi.randomPassword());
        request.put("birthDate", LocalDate.now().minusYears(15).toString());

        assertThat(patients.selfRegister(publicCode, request, null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("birthDate"))
                .hasPathSatisfying("$.errors[0].code", code -> assertThat(code).asString().isEqualTo("MINOR_NOT_ALLOWED"));
        assertThat(countPatients()).isZero();
    }

    @Test
    void selfRegistrationIsIdempotent() {
        enable();
        Map<String, Object> request = PatientApi.selfRegistration(IdentityApi.uniqueEmail("self"), IdentityApi.randomPassword());
        String key = UUID.randomUUID().toString();

        assertThat(patients.selfRegister(publicCode, request, key)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(patients.selfRegister(publicCode, PatientApi.copy(request), key)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(countPatients()).isEqualTo(1);

        Map<String, Object> other = PatientApi.selfRegistration(IdentityApi.uniqueEmail("self"), IdentityApi.randomPassword());
        assertThat(patients.selfRegister(publicCode, other, key))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void settingsUseTheVersion() {
        Map<String, Object> enabled = api.body(patients.updateSettings(clinic.token(), true, 0));
        assertThat(enabled).containsEntry("selfRegistrationEnabled", true).containsEntry("version", 1);
        contract.assertResponse("/patient-registration-settings", "PUT", 200, "application/json", enabled);

        assertThat(patients.updateSettings(clinic.token(), false, 0))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where tenant_id = ? and action = 'PATIENT_REGISTRATION_SETTINGS_UPDATED'",
                Long.class, clinic.tenantId())).isEqualTo(1);
    }

    private void enable() {
        long version = ((Number) api.body(patients.settings(clinic.token())).get("version")).longValue();
        assertThat(patients.updateSettings(clinic.token(), true, version)).hasStatus(HttpStatus.OK);
    }

    private long countPatients() {
        return jdbc.queryForObject("select count(*) from patients_patient where tenant_id = ?", Long.class, clinic.tenantId());
    }
}
