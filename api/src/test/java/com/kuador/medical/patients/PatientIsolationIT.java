package com.kuador.medical.patients;

import static com.kuador.medical.patients.PatientApi.adult;
import static com.kuador.medical.patients.PatientApi.id;
import static com.kuador.medical.patients.PatientApi.minor;
import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * Isolation A↔B of patients, guardians, guardian links, consents and self-registration settings
 * (Constitution I): a clinic never reads nor changes the records of another one, and their
 * existence is never revealed (404).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class PatientIsolationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingNotificationSender mail;

    private IdentityApi api;
    private PatientApi patients;
    private Clinic clinicA;
    private Clinic clinicB;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        patients = new PatientApi(mvc, jsonMapper, mail);
        clinicA = api.clinic("Clinica A");
        clinicB = api.clinic("Clinica B");
    }

    @Test
    void patientsOfAnotherClinicAreNotFound() {
        Map<String, Object> request = adult();
        request.put("email", IdentityApi.uniqueEmail("patient"));
        UUID patientOfA = id(api.body(patients.register(clinicA.token(), request)));

        assertThat(patients.get(clinicB.token(), patientOfA)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patients.patch(clinicB.token(), patientOfA, Map.of("phone", "+10000000000", "version", 0)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patients.recordConsent(clinicB.token(), patientOfA, PatientApi.consent("PATIENT"), null))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patients.links(clinicB.token(), patientOfA)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patients.inviteAccount(clinicB.token(), patientOfA, "EMAIL")).hasStatus(HttpStatus.NOT_FOUND);

        Map<String, Object> pageOfB = api.body(patients.list(clinicB.token(), "?documentType=NATIONAL_ID&documentNumber="
                + request.get("documentNumber")));
        assertThat((List<Object>) pageOfB.get("items")).isEmpty();
        assertThat(pageOfB).containsEntry("totalItems", 0);

        assertThat(jdbc.queryForObject("select count(*) from patients_consent where patient_id = ?", Long.class, patientOfA))
                .isEqualTo(1);
    }

    @Test
    void guardiansAndLinksOfAnotherClinicAreNotFound() {
        UUID minorOfA = id(api.body(patients.register(clinicA.token(), minor())));
        Map<String, Object> linkOfA = api.bodyList(patients.links(clinicA.token(), minorOfA)).getFirst();
        UUID guardianOfA = UUID.fromString(((Map<String, Object>) linkOfA.get("guardian")).get("id").toString());

        assertThat(patients.endLink(clinicB.token(), minorOfA, linkOfA.get("id"))).hasStatus(HttpStatus.NOT_FOUND);

        // A minor of B cannot be linked to the guardian of A: the guardian does not exist in B.
        UUID minorOfB = id(api.body(patients.register(clinicB.token(), minor())));
        assertThat(patients.createLink(clinicB.token(), minorOfB,
                        Map.of("guardianId", guardianOfA.toString(), "relationship", "Aunt"), null))
                .hasStatus(HttpStatus.NOT_FOUND);

        // Nor can a link of A be ended through a patient of B.
        assertThat(patients.endLink(clinicB.token(), minorOfB, linkOfA.get("id"))).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.bodyList(patients.links(clinicA.token(), minorOfA)).getFirst()).containsEntry("current", true);
    }

    @Test
    void selfRegistrationSettingsBelongToEachClinic() {
        Map<String, Object> settingsOfA = api.body(patients.settings(clinicA.token()));
        assertThat(patients.updateSettings(clinicA.token(), true, 0)).hasStatus(HttpStatus.OK);

        Map<String, Object> settingsOfB = api.body(patients.settings(clinicB.token()));
        assertThat(settingsOfB).containsEntry("selfRegistrationEnabled", false);
        assertThat(settingsOfB.get("publicCode")).isNotEqualTo(settingsOfA.get("publicCode"));

        // A registration through the public code of A lands in A only.
        Map<String, Object> request = PatientApi.selfRegistration(IdentityApi.uniqueEmail("self"), IdentityApi.randomPassword());
        assertThat(patients.selfRegister(settingsOfA.get("publicCode").toString(), request, null)).hasStatus(HttpStatus.ACCEPTED);
        assertThat(jdbc.queryForList("select tenant_id from patients_patient where document_number = ?", UUID.class,
                request.get("documentNumber"))).containsExactly(clinicA.tenantId());
    }
}
