package com.kuador.medical.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.IdentityApi;
import db.localseed.LocalSeedMigration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * The api in the "local" profile (MEDK-26 plan, decisions 2 and 3): the seed creates clinics A and
 * B with one test user each, whose password is LOCAL_SEED_PASSWORD; Actuator only serves health;
 * no credential or personal data reaches the logs.
 */
@LocalProfileTest
@ExtendWith(OutputCaptureExtension.class)
class LocalProfileIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private IdentityApi api;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
    }

    @Test
    void seedCreatesTwoClinicsWithOneBranchAndOneUserEach() {
        assertThat(jdbc.queryForList("select name from tenant where id in (?, ?) order by name", String.class,
                        LocalSeedMigration.TENANT_A, LocalSeedMigration.TENANT_B))
                .containsExactly(LocalSeedMigration.CLINIC_A_NAME, LocalSeedMigration.CLINIC_B_NAME);
        assertThat(jdbc.queryForObject("select count(*) from clinics_branch where tenant_id = ?", Integer.class,
                LocalSeedMigration.TENANT_A)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from identity_user_account where tenant_id = ?", Integer.class,
                LocalSeedMigration.TENANT_B)).isEqualTo(1);
    }

    @Test
    void seedStoresOnlyAHashOfThePassword() {
        String hash = jdbc.queryForObject("select password_hash from identity_user_account where id = ?", String.class,
                LocalSeedMigration.USER_A);
        assertThat(hash).startsWith("{argon2}").doesNotContain(LocalProfileTest.SEED_PASSWORD);
    }

    @Test
    void seedUserOfClinicALogsInWithTheSeedPasswordAndSeesItsClinic() {
        Map<String, Object> session = api.session(LocalSeedMigration.USER_A_EMAIL, LocalProfileTest.SEED_PASSWORD);

        assertThat(session).containsEntry("scope", "TENANT");
        assertThat(clinicOf(session)).containsEntry("id", LocalSeedMigration.TENANT_A.toString())
                .containsEntry("name", LocalSeedMigration.CLINIC_A_NAME);

        Map<String, Object> me = api.body(api.me(session.get("accessToken").toString()));
        assertThat(clinicOf(me)).containsEntry("name", LocalSeedMigration.CLINIC_A_NAME);
        assertThat(me).containsEntry("mustChangePassword", false);
    }

    @Test
    void seedUserOfClinicBLogsInAndSeesItsClinic() {
        Map<String, Object> session = api.session(LocalSeedMigration.USER_B_EMAIL, LocalProfileTest.SEED_PASSWORD);

        assertThat(clinicOf(session)).containsEntry("id", LocalSeedMigration.TENANT_B.toString())
                .containsEntry("name", LocalSeedMigration.CLINIC_B_NAME);
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThat(api.login(LocalSeedMigration.USER_A_EMAIL, "not-the-seed-password")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthIsPublicAndUpWithTheDatabase() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPathSatisfying("$.status", status -> assertThat(status).asString().isEqualTo("UP"))
                .hasPathSatisfying("$.components.db.status", status -> assertThat(status).asString().isEqualTo("UP"))
                .doesNotHavePath("$.components.db.details");
    }

    @Test
    void actuatorExposesNothingButHealth() {
        for (String endpoint : List.of("env", "info", "beans", "configprops", "metrics", "loggers", "mappings",
                "heapdump", "threaddump", "shutdown", "flyway")) {
            assertThat(mvc.get().uri("/actuator/" + endpoint)).as(endpoint).hasStatus(HttpStatus.NOT_FOUND);
        }
        assertThat(mvc.post().uri("/actuator/shutdown")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void loginDoesNotLogThePasswordNorTheEmail(CapturedOutput output) {
        api.session(LocalSeedMigration.USER_A_EMAIL, LocalProfileTest.SEED_PASSWORD);
        api.login(LocalSeedMigration.USER_B_EMAIL, "not-the-seed-password");

        assertThat(output.getAll())
                .doesNotContain(LocalProfileTest.SEED_PASSWORD)
                .doesNotContain("not-the-seed-password")
                .doesNotContain(LocalSeedMigration.USER_A_EMAIL)
                .doesNotContain(LocalSeedMigration.USER_B_EMAIL);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> clinicOf(Map<String, Object> body) {
        return (Map<String, Object>) body.get("clinic");
    }
}
