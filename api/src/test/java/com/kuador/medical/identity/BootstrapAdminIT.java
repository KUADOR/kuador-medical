package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.identity.bootstrap.BootstrapAdminInitializer;
import com.kuador.medical.identity.bootstrap.BootstrapAdminProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Initial SUPER_ADMIN account (FR-013, SC-007): created at startup from the environment, only
 * once, and blocked until its initial password is changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    ScopeFixtureControllers.ClinicEndpoint.class,
    ScopeFixtureControllers.PlatformEndpoint.class
})
class BootstrapAdminIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BootstrapAdminProperties bootstrap;

    @Autowired
    private BootstrapAdminInitializer initializer;

    private IdentityApi api;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
    }

    @Test
    void initialAccountIsAPlatformAdminWithoutClinicAndAHashedPassword() {
        var account = jdbc.queryForMap(
                "select platform_admin, password_hash from user_account where email = ?", bootstrap.email());

        assertThat(account.get("platform_admin")).isEqualTo(true);
        assertThat(account.get("password_hash").toString()).startsWith("{argon2}").doesNotContain(bootstrap.password());
        assertThat(jdbc.queryForObject(
                "select count(*) from tenant_membership m join user_account a on a.id = m.user_account_id where a.email = ?",
                Integer.class, bootstrap.email())).isZero();
    }

    @Test
    void creationIsIdempotent() {
        String hashBefore = passwordHash();
        int adminsBefore = platformAdmins();

        initializer.createIfMissing();
        initializer.createIfMissing();

        assertThat(platformAdmins()).isEqualTo(adminsBefore);
        assertThat(passwordHash()).isEqualTo(hashBefore);
    }

    @Test
    void firstLoginIsBlockedUntilThePasswordIsChanged() {
        assertThat(jdbc.queryForObject(
                "select must_change_password from user_account where email = ?", Boolean.class, bootstrap.email()))
                .isTrue();
        MvcTestResult login = api.login(bootstrap.email(), bootstrap.password());
        assertThat(login).hasStatus(HttpStatus.OK);
        Map<String, Object> loginBody = api.body(login);
        assertThat(loginBody).containsEntry("scope", "PLATFORM").containsEntry("mustChangePassword", true);
        String token = loginBody.get("accessToken").toString();

        // Blocked: every operation other than /me and /auth/password.
        assertThat(api.get("/api/v1/test/platform-operation", token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("PASSWORD_CHANGE_REQUIRED"));
        ContractSchema.identity().assertResponse("/me", "GET", 200, "application/json", api.body(api.me(token)));
        Map<String, Object> me = api.body(api.me(token));
        assertThat(me).containsEntry("scope", "PLATFORM").containsEntry("mustChangePassword", true).doesNotContainKey("tenant");
        assertThat((List<Object>) me.get("roles")).containsExactly("SUPER_ADMIN");
        assertThat((List<Object>) me.get("permissions")).containsExactly("platform.permissions.read");

        // Change: the same token now operates the platform; the initial password is dead.
        String newPassword = IdentityApi.randomPassword();
        assertThat(api.changePassword(token, bootstrap.password(), newPassword)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(api.get("/api/v1/test/platform-operation", token)).hasStatus(HttpStatus.OK);
        assertThat(api.body(api.me(token))).containsEntry("mustChangePassword", false);
        assertThat(api.login(bootstrap.email(), bootstrap.password()))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("INVALID_CREDENTIALS"));
        assertThat(api.login(bootstrap.email(), newPassword)).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event e join user_account a on a.id = e.actor_account_id "
                        + "where a.email = ? and e.action = 'PASSWORD_CHANGED' and e.tenant_id is null",
                Integer.class, bootstrap.email())).isOne();

        // Re-running the bootstrap never restores the initial password.
        initializer.createIfMissing();
        assertThat(api.login(bootstrap.email(), bootstrap.password())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private int platformAdmins() {
        return jdbc.queryForObject("select count(*) from user_account where platform_admin", Integer.class);
    }

    private String passwordHash() {
        return jdbc.queryForObject("select password_hash from user_account where email = ?", String.class, bootstrap.email());
    }
}
