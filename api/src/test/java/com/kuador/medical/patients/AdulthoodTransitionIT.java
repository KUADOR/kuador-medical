package com.kuador.medical.patients;

import static com.kuador.medical.patients.PatientApi.id;
import static com.kuador.medical.patients.PatientApi.minor;
import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.clinics.ClinicCalendar;
import com.kuador.medical.identity.StaffFixture;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.patients.registration.AdulthoodJob;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
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
 * Transition to adulthood (MEDK-18, § Transición a la mayoría de edad of the plan of MEDK-12):
 * the daily job marks the patient as adult on the 18th birthday in the time zone of the clinic,
 * ends the legal guardian links as history (ADULTHOOD) so the guardian loses current access,
 * invites the patient to activate an account, audits every step, is idempotent, runs once across
 * replicas (distributed lock) and processes each clinic with its own tenant context. The adult
 * patient then authorizes links on {@code /me/guardian-links}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class AdulthoodTransitionIT {

    private static final Pattern INVITATION_LINK = Pattern.compile("/invitation#t=([A-Za-z0-9_-]+)");

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

    @Autowired
    private ClinicCalendar calendar;

    @Autowired
    private AdulthoodJob job;

    private final ContractSchema contract = ContractSchema.userRegistration();
    private IdentityApi api;
    private PatientApi patients;
    private StaffFixture staff;
    private Clinic clinic;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        patients = new PatientApi(mvc, jsonMapper, mail);
        staff = new StaffFixture(jdbc, passwordEncoder);
        clinic = api.clinic("Clinica Mayoria");
        staff.branch(clinic.tenantId(), "Matriz");
    }

    @AfterEach
    void releaseLock() {
        expireLock();
    }

    // ------------------------------------------------------------------ the job

    @Test
    void patientTurning18IsMarkedAdultItsLegalGuardianLinkEndsAndItIsInvited() {
        String email = IdentityApi.uniqueEmail("turning18");
        UUID patient = minorTurningAdultToday(clinic, email);
        LocalDate birthDate = birthDate(patient);

        assertThat(job.runOnce()).isTrue();

        assertThat(jdbc.queryForObject("select adult_since from patients_patient where id = ?", LocalDate.class, patient))
                .isEqualTo(birthDate.plusYears(18));
        Map<String, Object> body = api.body(patients.get(clinic.token(), patient));
        assertThat(body)
                .containsEntry("minor", false)
                .containsEntry("adultSince", birthDate.plusYears(18).toString())
                .containsEntry("accountStatus", "INVITED");

        List<Map<String, Object>> links = api.bodyList(patients.links(clinic.token(), patient));
        assertThat(links).singleElement().satisfies(link -> assertThat(link)
                .containsEntry("linkType", "LEGAL_GUARDIAN")
                .containsEntry("current", false)
                .containsEntry("endReason", "ADULTHOOD")
                .containsKey("validTo"));
        assertThat(links.getFirst().get("validTo")).isNotNull();

        assertThat(mail.awaitSentTo(email, 1)).hasSize(1);
        assertThat(audited(patient)).contains("PATIENT_BECAME_ADULT", "GUARDIAN_LINK_ENDED", "PATIENT_ACCOUNT_INVITED");
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event
                where target_id = ? and action = 'PATIENT_BECAME_ADULT' and actor_account_id is null and tenant_id = ?""",
                Long.class, patient, clinic.tenantId())).isEqualTo(1);
    }

    @Test
    void theJobIsIdempotent() {
        String email = IdentityApi.uniqueEmail("idempotent");
        UUID patient = minorTurningAdultToday(clinic, email);

        assertThat(job.runOnce()).isTrue();
        Object validTo = jdbc.queryForObject(
                "select valid_to from patients_guardian_link where patient_id = ?", Object.class, patient);
        List<String> firstRun = audited(patient);
        mail.awaitSentTo(email, 1);

        assertThat(job.runOnce()).isTrue();

        assertThat(jdbc.queryForObject("select valid_to from patients_guardian_link where patient_id = ?", Object.class, patient))
                .isEqualTo(validTo);
        assertThat(audited(patient)).isEqualTo(firstRun);
        assertThat(mail.sentTo(email)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from identity_user_account where patient_ref = ?", Long.class, patient))
                .isEqualTo(1);
    }

    @Test
    void aMinorTurning18TomorrowAndAPatientRegisteredAsAdultAreNotTouched() {
        UUID tomorrow = registerMinor(clinic, IdentityApi.uniqueEmail("tomorrow"), null);
        setBirthDate(tomorrow, calendar.today().minusYears(18).plusDays(1));
        UUID adult = id(api.body(patients.register(clinic.token(), PatientApi.adult())));

        assertThat(job.runOnce()).isTrue();

        assertThat(jdbc.queryForObject("select adult_since from patients_patient where id = ?", LocalDate.class, tomorrow)).isNull();
        assertThat(api.bodyList(patients.links(clinic.token(), tomorrow)))
                .singleElement().satisfies(link -> assertThat(link).containsEntry("current", true));
        assertThat(jdbc.queryForObject("select adult_since from patients_patient where id = ?", LocalDate.class, adult)).isNull();
        assertThat(audited(tomorrow)).doesNotContain("PATIENT_BECAME_ADULT");
        assertThat(audited(adult)).doesNotContain("PATIENT_BECAME_ADULT");
    }

    @Test
    void theGuardianKeepsTheLinksOfItsOtherMinors() {
        UUID adult = minorTurningAdultToday(clinic, IdentityApi.uniqueEmail("older"));
        UUID guardian = UUID.fromString(jdbc.queryForObject(
                "select guardian_id from patients_guardian_link where patient_id = ?", String.class, adult));
        Map<String, Object> sibling = minor();
        sibling.put("guardian", Map.of("relationship", "Mother", "guardianId", guardian.toString()));
        UUID minor = id(api.body(patients.register(clinic.token(), sibling)));

        assertThat(job.runOnce()).isTrue();

        assertThat(currentLinksOf(guardian)).containsExactly(minor);
    }

    @Test
    void withoutEmailTheInvitationStaysPendingAndIsAudited() {
        UUID patient = registerMinor(clinic, null, "+593991234567");
        setBirthDate(patient, calendar.today().minusYears(18));

        assertThat(job.runOnce()).isTrue();

        assertThat(jdbc.queryForObject("select adult_since from patients_patient where id = ?", LocalDate.class, patient)).isNotNull();
        assertThat(jdbc.queryForObject("select account_ref from patients_patient where id = ?", Object.class, patient)).isNull();
        assertThat(audited(patient)).contains("PATIENT_BECAME_ADULT", "GUARDIAN_LINK_ENDED", "ADULTHOOD_INVITATION_PENDING")
                .doesNotContain("PATIENT_ACCOUNT_INVITED");
    }

    @Test
    void eachClinicIsProcessedWithItsOwnTenantContext() {
        Clinic other = api.clinic("Clinica Mayoria B");
        UUID ofA = minorTurningAdultToday(clinic, IdentityApi.uniqueEmail("tenant-a"));
        UUID ofB = minorTurningAdultToday(other, IdentityApi.uniqueEmail("tenant-b"));

        assertThat(job.runOnce()).isTrue();

        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where target_id = ? and action = 'PATIENT_BECAME_ADULT' and tenant_id = ?""",
                Long.class, ofA, clinic.tenantId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where target_id = ? and action = 'PATIENT_BECAME_ADULT' and tenant_id = ?""",
                Long.class, ofB, other.tenantId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select tenant_id from identity_user_account where patient_ref = ?", UUID.class, ofB)).isEqualTo(other.tenantId());
        // Neither clinic sees the patient of the other.
        assertThat(patients.get(clinic.token(), ofB)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patients.get(other.token(), ofA)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRunIsSkippedWhileAnotherReplicaHoldsTheLock() {
        job.runOnce(); // the lock row exists from now on
        jdbc.update("""
                update shedlock set lock_until = timezone('utc', now()) + interval '1 hour', locked_by = 'another-replica'
                where name = ?""", AdulthoodJob.LOCK_NAME);
        UUID patient = minorTurningAdultToday(clinic, IdentityApi.uniqueEmail("locked"));

        assertThat(job.runOnce()).isFalse();
        assertThat(jdbc.queryForObject("select adult_since from patients_patient where id = ?", LocalDate.class, patient)).isNull();

        expireLock();
        assertThat(job.runOnce()).isTrue();
        assertThat(jdbc.queryForObject("select adult_since from patients_patient where id = ?", LocalDate.class, patient)).isNotNull();
    }

    // ------------------------------------------------------------------ /me/guardian-links

    @Test
    void theAdultPatientListsItsLinksAndAuthorizesItsFormerGuardian() {
        String email = IdentityApi.uniqueEmail("adult-patient");
        UUID patient = minorTurningAdultToday(clinic, email);
        UUID guardian = UUID.fromString(jdbc.queryForObject(
                "select guardian_id from patients_guardian_link where patient_id = ?", String.class, patient));
        job.runOnce();
        String token = activate(email);

        MvcTestResult list = myLinks(token);
        assertThat(list).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> history = api.bodyList(list);
        assertThat(history).singleElement().satisfies(link -> assertThat(link)
                .containsEntry("linkType", "LEGAL_GUARDIAN")
                .containsEntry("endReason", "ADULTHOOD")
                .containsEntry("current", false));
        contract.assertArrayResponse("/me/guardian-links", "GET", 200, "application/json", history);

        MvcTestResult created = authorize(token, Map.of(
                "guardianId", guardian.toString(), "linkType", "EMERGENCY_CONTACT", "relationship", "Mother"), null);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        Map<String, Object> link = api.body(created);
        UUID account = jdbc.queryForObject("select account_ref from patients_patient where id = ?", UUID.class, patient);
        assertThat(link)
                .containsEntry("linkType", "EMERGENCY_CONTACT")
                .containsEntry("current", true)
                .containsEntry("authorizedBy", account.toString());
        contract.assertResponse("/me/guardian-links", "POST", 201, "application/json", link);
        assertThat(api.bodyList(patients.links(clinic.token(), patient))).hasSize(2);
        assertThat(currentLinksOf(guardian)).containsExactly(patient);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event
                where target_id = ? and action = 'GUARDIAN_LINK_AUTHORIZED' and actor_account_id = ?""",
                Long.class, patient, account)).isEqualTo(1);
    }

    @Test
    void theAdultPatientAuthorizesANewPersonWithDelegatedAccessIdempotently() {
        String email = IdentityApi.uniqueEmail("delegating");
        minorTurningAdultToday(clinic, email);
        job.runOnce();
        String token = activate(email);
        Map<String, Object> request = Map.of(
                "person", PatientApi.guardianPerson(), "linkType", "DELEGATED_ACCESS", "relationship", "Spouse");

        MvcTestResult first = authorize(token, request, "authorize-1");
        MvcTestResult second = authorize(token, request, "authorize-1");

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(api.body(second).get("id")).isEqualTo(api.body(first).get("id"));
        assertThat(api.body(first)).containsEntry("linkType", "DELEGATED_ACCESS");
        assertThat(api.bodyList(myLinks(token))).hasSize(2);
    }

    @Test
    void onlyEmergencyContactOrDelegatedAccessOfAKnownGuardianCanBeAuthorized() {
        String email = IdentityApi.uniqueEmail("rules");
        UUID patient = minorTurningAdultToday(clinic, email);
        UUID guardian = UUID.fromString(jdbc.queryForObject(
                "select guardian_id from patients_guardian_link where patient_id = ?", String.class, patient));
        job.runOnce();
        String token = activate(email);

        assertThat(authorize(token, Map.of(
                "guardianId", guardian.toString(), "linkType", "LEGAL_GUARDIAN", "relationship", "Mother"), null))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(authorize(token, Map.of("linkType", "EMERGENCY_CONTACT", "relationship", "Mother"), null))
                .hasStatus(HttpStatus.BAD_REQUEST);

        // A guardian of another clinic is unknown here.
        Clinic other = api.clinic("Clinica Mayoria Otra");
        staff.branch(other.tenantId(), "Matriz");
        UUID foreignPatient = registerMinor(other, null, null);
        String foreignGuardian = jdbc.queryForObject(
                "select guardian_id from patients_guardian_link where patient_id = ?", String.class, foreignPatient);
        assertThat(authorize(token, Map.of(
                "guardianId", foreignGuardian, "linkType", "EMERGENCY_CONTACT", "relationship", "Mother"), null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[*].code", codes -> assertThat(codes).asList().contains("UNKNOWN_GUARDIAN"));
        assertThat(api.bodyList(patients.links(clinic.token(), patient))).hasSize(1);
    }

    @Test
    void staffAccountsCannotUseMyGuardianLinks() {
        assertThat(myLinks(clinic.token())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(authorize(clinic.token(), Map.of(
                "guardianId", UUID.randomUUID().toString(), "linkType", "EMERGENCY_CONTACT", "relationship", "Mother"), null))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/me/guardian-links").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------ helpers

    /** Ends any lock of the job; the row stays, as ShedLock keeps it between runs. */
    private void expireLock() {
        jdbc.update("update shedlock set lock_until = timezone('utc', now()) where name = ?", AdulthoodJob.LOCK_NAME);
    }

    /** A minor registered with a legal guardian whose 18th birthday is today in the clinic. */
    private UUID minorTurningAdultToday(Clinic target, String email) {
        UUID patient = registerMinor(target, email, null);
        setBirthDate(patient, calendar.today().minusYears(18));
        return patient;
    }

    private UUID registerMinor(Clinic target, String email, String phone) {
        Map<String, Object> request = minor();
        if (email != null) {
            request.put("email", email);
        }
        if (phone != null) {
            request.put("phone", phone);
        }
        MvcTestResult result = patients.register(target.token(), request);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return id(api.body(result));
    }

    private void setBirthDate(UUID patient, LocalDate birthDate) {
        jdbc.update("update patients_patient set birth_date = ? where id = ?", birthDate, patient);
    }

    private LocalDate birthDate(UUID patient) {
        return jdbc.queryForObject("select birth_date from patients_patient where id = ?", LocalDate.class, patient);
    }

    private List<UUID> currentLinksOf(UUID guardian) {
        return jdbc.queryForList(
                "select patient_id from patients_guardian_link where guardian_id = ? and valid_to is null", UUID.class, guardian);
    }

    /** Accepts the invitation emailed to the new adult and returns its access token. */
    private String activate(String email) {
        List<EmailMessage> sent = mail.awaitSentTo(email, 1);
        assertThat(sent).isNotEmpty();
        Matcher matcher = INVITATION_LINK.matcher(sent.getLast().body());
        assertThat(matcher.find()).as("invitation link in the email").isTrue();
        String password = IdentityApi.randomPassword();
        assertThat(api.publicPost("/api/v1/auth/invitations/accept", Map.of("token", matcher.group(1), "password", password)))
                .hasStatus2xxSuccessful();
        return api.token(email, password);
    }

    private MvcTestResult myLinks(String token) {
        return api.get("/api/v1/me/guardian-links", token);
    }

    private MvcTestResult authorize(String token, Map<String, Object> body, String idempotencyKey) {
        var builder = mvc.post().uri("/api/v1/me/guardian-links")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            builder = builder.header("Idempotency-Key", idempotencyKey);
        }
        return builder.exchange();
    }

    private List<String> audited(UUID target) {
        return jdbc.queryForList("select action from audit_event where target_id = ? order by occurred_at", String.class, target);
    }
}
