package com.kuador.medical.patients;

import static com.kuador.medical.patients.PatientApi.adult;
import static com.kuador.medical.patients.PatientApi.copy;
import static com.kuador.medical.patients.PatientApi.id;
import static com.kuador.medical.patients.PatientApi.minor;
import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.StaffFixture;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Patient registration by the staff (MEDK-17): mandatory data and LOPDP consent, document unique
 * per clinic, minors with a current legal guardian and without account, permissions of reception
 * and other roles, idempotent creations, guardian links, account invitation and audit of reads
 * and changes (rules 4, 5 and 7 of the plan of MEDK-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class PatientRegistrationIT {

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
    private PatientApi patients;
    private StaffFixture staff;
    private Clinic clinic;
    private UUID branch;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        patients = new PatientApi(mvc, jsonMapper, mail);
        staff = new StaffFixture(jdbc, passwordEncoder);
        clinic = api.clinic("Clinica Pacientes");
        branch = staff.branch(clinic.tenantId(), "Matriz");
    }

    @Test
    void receptionRegistersAnAdultPatientWithItsConsent() {
        String reception = PatientApi.staffToken(api, staff, clinic.tenantId(), branch, "RECEPTION");
        Map<String, Object> request = adult();

        MvcTestResult result = patients.register(reception, request);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        Map<String, Object> body = api.body(result);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/patients/" + id(body));
        assertThat(body)
                .containsEntry("documentNumber", request.get("documentNumber"))
                .containsEntry("minor", false)
                .containsEntry("origin", "STAFF")
                .containsEntry("accountStatus", "NONE")
                .containsEntry("version", 0);
        assertThat((Map<String, Object>) body.get("currentConsent"))
                .containsEntry("textVersion", "2026-01")
                .containsEntry("grantedBy", "PATIENT")
                .containsEntry("channel", "WEB");
        contract.assertResponse("/patients", "POST", 201, "application/json", body);
        assertThat(audited(id(body))).contains("PATIENT_REGISTERED", "PATIENT_CONSENT_RECORDED");
    }

    @Test
    void mandatoryDataAndConsentAreRequired() {
        Map<String, Object> request = adult();
        request.remove("consent");
        request.remove("birthDate");
        request.remove("sex");

        assertThat(patients.register(clinic.token(), request))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"))
                .hasPathSatisfying("$.errors[*].field", fields -> assertThat(fields).asList()
                        .containsExactlyInAnyOrder("birthDate", "consent", "sex"))
                .hasPathSatisfying("$.errors[*].code", codes -> assertThat(codes).asList().containsOnly("REQUIRED"));
        assertThat(countPatients()).isZero();
    }

    @Test
    void birthDateCannotBeInTheFuture() {
        Map<String, Object> request = PatientApi.patient(LocalDate.now().plusDays(2));

        assertThat(patients.register(clinic.token(), request))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("birthDate"));
    }

    @Test
    void documentIsUniquePerClinic() {
        Map<String, Object> request = adult();
        assertThat(patients.register(clinic.token(), request)).hasStatus(HttpStatus.CREATED);

        Map<String, Object> again = copy(request);
        again.put("firstNames", "Other");
        assertThat(patients.register(clinic.token(), again))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("PATIENT_DUPLICATE"));

        // Another clinic may register the same document.
        Clinic other = api.clinic("Otra Clinica");
        assertThat(patients.register(other.token(), copy(request))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void aMinorRequiresACurrentLegalGuardianAndNeverGetsAnAccount() {
        Map<String, Object> withoutGuardian = minor();
        withoutGuardian.remove("guardian");

        assertThat(patients.register(clinic.token(), withoutGuardian))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("guardian"))
                .hasPathSatisfying("$.errors[0].code", code -> assertThat(code).asString().isEqualTo("REQUIRED"));

        Map<String, Object> consentByPatient = minor();
        consentByPatient.put("consent", PatientApi.consent("PATIENT"));
        assertThat(patients.register(clinic.token(), consentByPatient))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("consent.grantedBy"));
        assertThat(countPatients()).isZero();

        MvcTestResult created = patients.register(clinic.token(), minor());
        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> body = api.body(created);
        assertThat(body).containsEntry("minor", true).containsEntry("accountStatus", "NONE");

        List<Map<String, Object>> links = api.bodyList(patients.links(clinic.token(), id(body)));
        assertThat(links).singleElement().satisfies(link -> assertThat(link)
                .containsEntry("linkType", "LEGAL_GUARDIAN")
                .containsEntry("relationship", "Mother")
                .containsEntry("current", true));
        contract.assertArrayResponse("/patients/{id}/guardian-links", "GET", 200, "application/json", links);

        assertThat(patients.inviteAccount(clinic.token(), id(body), "EMAIL"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("MINOR_CANNOT_HAVE_ACCOUNT"));
        assertThat(jdbc.queryForObject("select count(*) from identity_user_account where tenant_id = ? and kind <> 'STAFF'",
                Long.class, clinic.tenantId())).isZero();
    }

    @Test
    void theLastLegalGuardianOfAMinorCannotBeEnded() {
        UUID patient = id(api.body(patients.register(clinic.token(), minor())));
        Map<String, Object> first = api.bodyList(patients.links(clinic.token(), patient)).getFirst();

        assertThat(patients.endLink(clinic.token(), patient, first.get("id")))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("LAST_LEGAL_GUARDIAN"));

        MvcTestResult second = patients.createLink(clinic.token(), patient,
                Map.of("relationship", "Father", "person", PatientApi.guardianPerson()), null);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        contract.assertResponse("/patients/{id}/guardian-links", "POST", 201, "application/json", api.body(second));

        MvcTestResult ended = patients.endLink(clinic.token(), patient, first.get("id"));
        assertThat(ended).hasStatus(HttpStatus.OK);
        assertThat(api.body(ended)).containsEntry("current", false).containsEntry("endReason", "MANUAL");
        assertThat(audited(patient)).contains("GUARDIAN_LINK_CREATED", "GUARDIAN_LINK_ENDED");

        // Ending it again returns it unchanged.
        assertThat(patients.endLink(clinic.token(), patient, first.get("id"))).hasStatus(HttpStatus.OK);
        assertThat(api.bodyList(patients.links(clinic.token(), patient))).hasSize(2);
    }

    @Test
    void linksOfAnAdultNeedTheAuthorizationOfThePatient() {
        UUID patient = id(api.body(patients.register(clinic.token(), adult())));

        assertThat(patients.createLink(clinic.token(), patient,
                        Map.of("relationship", "Mother", "person", PatientApi.guardianPerson()), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ADULT_AUTHORIZATION_REQUIRED"));
    }

    @Test
    void rolesWithoutPermissionAreForbidden() {
        String doctor = PatientApi.staffToken(api, staff, clinic.tenantId(), branch, "DOCTOR");
        UUID patient = id(api.body(patients.register(clinic.token(), adult())));

        assertThat(patients.register(doctor, adult())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patients.list(doctor, "")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patients.get(doctor, patient)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patients.patch(doctor, patient, Map.of("phone", "+10000000000", "version", 0)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patients.updateSettings(doctor, true, 0)).hasStatus(HttpStatus.FORBIDDEN);

        String reception = PatientApi.staffToken(api, staff, clinic.tenantId(), branch, "RECEPTION");
        // Reception registers patients but does not configure self-registration.
        assertThat(patients.updateSettings(reception, true, 0)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patients.settings(reception)).hasStatus(HttpStatus.OK);
        assertThat(patients.register(clinic.token(), adult())).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void creationsAreIdempotent() {
        Map<String, Object> request = adult();
        String key = UUID.randomUUID().toString();

        MvcTestResult first = patients.register(clinic.token(), request, key);
        MvcTestResult replay = patients.register(clinic.token(), copy(request), key);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(id(api.body(replay))).isEqualTo(id(api.body(first)));
        assertThat(countPatients()).isEqualTo(1);

        Map<String, Object> different = adult();
        assertThat(patients.register(clinic.token(), different, key))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("IDEMPOTENCY_KEY_REUSED"));

        UUID clientId = UUID.randomUUID();
        Map<String, Object> withId = adult();
        withId.put("id", clientId.toString());
        assertThat(id(api.body(patients.register(clinic.token(), withId)))).isEqualTo(clientId);
        Map<String, Object> sameId = adult();
        sameId.put("id", clientId.toString());
        assertThat(patients.register(clinic.token(), sameId))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("RESOURCE_ID_TAKEN"));

        UUID patient = id(api.body(first));
        String consentKey = UUID.randomUUID().toString();
        MvcTestResult recorded = patients.recordConsent(clinic.token(), patient, Map.of("textVersion", "2026-02", "grantedBy", "PATIENT"), consentKey);
        MvcTestResult again = patients.recordConsent(clinic.token(), patient, Map.of("textVersion", "2026-02", "grantedBy", "PATIENT"), consentKey);
        assertThat(recorded).hasStatus(HttpStatus.CREATED);
        assertThat(id(api.body(again))).isEqualTo(id(api.body(recorded)));
        contract.assertResponse("/patients/{id}/consents", "POST", 201, "application/json", api.body(recorded));
        assertThat(jdbc.queryForObject("select count(*) from patients_consent where patient_id = ?", Long.class, patient))
                .isEqualTo(2);
    }

    @Test
    void readsAreAuditedAndUpdatesUseTheVersion() {
        Map<String, Object> request = adult();
        UUID patient = id(api.body(patients.register(clinic.token(), request)));

        MvcTestResult read = patients.get(clinic.token(), patient);
        assertThat(read).hasStatus(HttpStatus.OK);
        contract.assertResponse("/patients/{id}", "GET", 200, "application/json", api.body(read));
        assertThat(audited(patient)).contains("PATIENT_VIEWED");

        MvcTestResult page = patients.list(clinic.token(), "?q=" + request.get("documentNumber"));
        assertThat(page).hasStatus(HttpStatus.OK);
        Map<String, Object> pageBody = api.body(page);
        assertThat((List<Object>) pageBody.get("items")).hasSize(1);
        assertThat(pageBody).containsEntry("totalItems", 1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where tenant_id = ? and action = 'PATIENTS_LISTED'",
                Long.class, clinic.tenantId())).isEqualTo(1);

        MvcTestResult updated = patients.patch(clinic.token(), patient, Map.of("phone", "+10000000000", "version", 0));
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(api.body(updated)).containsEntry("phone", "+10000000000").containsEntry("version", 1);
        contract.assertResponse("/patients/{id}", "PATCH", 200, "application/json", api.body(updated));
        assertThat(audited(patient)).contains("PATIENT_UPDATED");

        assertThat(patients.patch(clinic.token(), patient, Map.of("phone", "+10000000001", "version", 0)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VERSION_CONFLICT"));

        // An adult cannot become a minor without a current legal guardian.
        assertThat(patients.patch(clinic.token(), patient,
                        Map.of("birthDate", LocalDate.now().minusYears(5).toString(), "version", 1)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("guardian"));
    }

    @Test
    void anAdultPatientIsInvitedToActivateItsAccount() {
        Map<String, Object> request = adult();
        String email = IdentityApi.uniqueEmail("patient");
        request.put("email", email);
        UUID patient = id(api.body(patients.register(clinic.token(), request)));

        assertThat(patients.inviteAccount(clinic.token(), patient, "SMS"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("CONTACT_CHANNEL_UNAVAILABLE"));

        assertThat(patients.inviteAccount(clinic.token(), patient, "EMAIL")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(mail.awaitSentTo(email, 1)).hasSize(1);
        assertThat(api.body(patients.get(clinic.token(), patient))).containsEntry("accountStatus", "INVITED");
        assertThat(audited(patient)).contains("PATIENT_ACCOUNT_INVITED");

        // A second request resends the invitation.
        assertThat(patients.inviteAccount(clinic.token(), patient, "EMAIL")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(mail.awaitSentTo(email, 2)).hasSize(2);

        UUID withoutEmail = id(api.body(patients.register(clinic.token(), adult())));
        assertThat(patients.inviteAccount(clinic.token(), withoutEmail, "EMAIL"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("CONTACT_CHANNEL_UNAVAILABLE"));
    }

    private long countPatients() {
        return jdbc.queryForObject("select count(*) from patients_patient where tenant_id = ?", Long.class, clinic.tenantId());
    }

    private List<String> audited(UUID target) {
        return jdbc.queryForList("select action from audit_event where target_id = ? order by occurred_at", String.class, target);
    }
}
