package com.kuador.medical.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.IdentityApi;
import db.localseed.LocalSeedMigration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tenant isolation with the local seed (Constitution I, MEDK-26 acceptance criteria 6 and 7): the
 * user of clinic A neither reads nor writes data of clinic B, not even when it sends an identifier
 * of B, because the clinic comes only from the access token.
 */
@LocalProfileTest
@SuppressWarnings("unchecked")
class LocalSeedTenantIsolationIT {

    private static final List<String> TENANT_HEADERS = List.of("X-Tenant-Id", "X-Clinic-Id", "X-Tenant");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        tokenA = api.token(LocalSeedMigration.USER_A_EMAIL, LocalProfileTest.SEED_PASSWORD);
        tokenB = api.token(LocalSeedMigration.USER_B_EMAIL, LocalProfileTest.SEED_PASSWORD);
    }

    @Test
    void meReturnsClinicAEvenWhenTheRequestNamesClinicB() {
        String b = LocalSeedMigration.TENANT_B.toString();
        var request = mvc.get().uri("/api/v1/me?tenantId={b}&clinicId={b}&tenant={b}", b, b, b)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA);
        for (String header : TENANT_HEADERS) {
            request = request.header(header, b);
        }

        MvcTestResult result = request.exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> clinic = (Map<String, Object>) api.body(result).get("clinic");
        assertThat(clinic).containsEntry("id", LocalSeedMigration.TENANT_A.toString())
                .containsEntry("name", LocalSeedMigration.CLINIC_A_NAME);
    }

    @Test
    void userOfANeitherReadsNorChangesTheRolesOfB() {
        UUID roleOfB = createRole(tokenB, "Rol de B " + UUID.randomUUID());
        String uri = "/api/v1/roles/" + roleOfB;

        assertThat(api.get(uri, tokenA)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.put(uri, tokenA, Map.of("name", "Cambiado por A", "permissions", List.of(), "version", 0)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.delete(uri, tokenA)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(ids(api.bodyList(api.get("/api/v1/roles", tokenA)))).doesNotContain(roleOfB.toString());

        assertThat(jdbc.queryForObject("select tenant_id from identity_role where id = ?", UUID.class, roleOfB))
                .isEqualTo(LocalSeedMigration.TENANT_B);
        assertThat(jdbc.queryForObject("select name from identity_role where id = ?", String.class, roleOfB))
                .startsWith("Rol de B ");
        assertThat(api.get(uri, tokenB)).hasStatus(HttpStatus.OK);
    }

    @Test
    void userOfANeitherReadsNorChangesTheStaffOfB() {
        String uri = "/api/v1/staff-users/" + LocalSeedMigration.USER_B;

        assertThat(api.get(uri, tokenA)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.patch().uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(Map.of("jobTitle", "Cambiado por A", "version", 0)))
                        .exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.post(uri + "/disable", tokenA, Map.of())).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject("select status from identity_user_account where id = ?", String.class,
                LocalSeedMigration.USER_B)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select job_title from identity_user_account where id = ?", String.class,
                LocalSeedMigration.USER_B)).isNull();
    }

    @Test
    void userOfANeitherReadsNorChangesThePatientsOfB() {
        Map<String, Object> patient = patient();
        MvcTestResult created = api.post("/api/v1/patients", tokenB, patient);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID patientOfB = UUID.fromString(api.body(created).get("id").toString());
        String uri = "/api/v1/patients/" + patientOfB;

        assertThat(api.get(uri, tokenA)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.patch().uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(Map.of("phone", "+10000000000", "version", 0)))
                        .exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        Map<String, Object> pageOfA = api.body(api.get("/api/v1/patients?documentType=NATIONAL_ID&documentNumber="
                + patient.get("documentNumber"), tokenA));
        assertThat((List<Object>) pageOfA.get("items")).isEmpty();

        assertThat(jdbc.queryForObject("select tenant_id from patients_patient where id = ?", UUID.class, patientOfB))
                .isEqualTo(LocalSeedMigration.TENANT_B);
    }

    @Test
    void anIdentifierOfBInTheBodyOrHeadersNeverWritesIntoB() {
        String name = "Rol con tenant de B " + UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("permissions", List.of());
        body.put("tenantId", LocalSeedMigration.TENANT_B.toString());
        body.put("clinicId", LocalSeedMigration.TENANT_B.toString());
        var request = mvc.post().uri("/api/v1/roles?tenantId={b}", LocalSeedMigration.TENANT_B)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(body));
        for (String header : TENANT_HEADERS) {
            request = request.header(header, LocalSeedMigration.TENANT_B.toString());
        }

        MvcTestResult result = request.exchange();

        // The unknown fields are either ignored (the role is created in A) or rejected; never written into B.
        assertThat(result.getResponse().getStatus()).isIn(201, 400);
        assertThat(jdbc.queryForList("select tenant_id from identity_role where name = ?", UUID.class, name))
                .doesNotContain(LocalSeedMigration.TENANT_B)
                .allMatch(LocalSeedMigration.TENANT_A::equals);
    }

    private UUID createRole(String token, String name) {
        MvcTestResult result = api.post("/api/v1/roles", token, Map.of("name", name, "permissions", List.of("roles.read")));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(api.body(result).get("id").toString());
    }

    private static List<String> ids(List<Map<String, Object>> items) {
        return items.stream().map(item -> item.get("id").toString()).toList();
    }

    private static Map<String, Object> patient() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("documentType", "NATIONAL_ID");
        request.put("documentNumber", String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 10));
        request.put("firstNames", "John");
        request.put("lastNames", "Roe");
        request.put("birthDate", LocalDate.now().minusYears(30).toString());
        request.put("sex", "MALE");
        request.put("consent", Map.of("textVersion", "2026-01", "grantedBy", "PATIENT"));
        return request;
    }
}
