package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Scope separation (FR-014): a PLATFORM token is rejected on clinic operations and a TENANT
 * token on platform operations, both with 403 FORBIDDEN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    ScopeFixtureControllers.ClinicEndpoint.class,
    ScopeFixtureControllers.PlatformEndpoint.class
})
class ScopeSeparationIT {

    private static final String CLINIC_OPERATION = "/api/v1/test/clinic-operation";
    private static final String PLATFORM_OPERATION = "/api/v1/test/platform-operation";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private IdentityApi api;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
    }

    @Test
    void platformTokenIsForbiddenOnClinicOperations() {
        String token = platformAdminToken();

        assertThat(api.get(CLINIC_OPERATION, token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("FORBIDDEN"));
        assertThat(api.get(PLATFORM_OPERATION, token)).hasStatus(HttpStatus.OK);
    }

    @Test
    void tenantTokenIsForbiddenOnPlatformOperations() {
        String email = IdentityApi.uniqueEmail("owner");
        String password = IdentityApi.randomPassword();
        api.signUp("Clinica Alcance", email, password);
        String token = api.token(email, password);

        assertThat(api.get(PLATFORM_OPERATION, token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("FORBIDDEN"));
        assertThat(api.get(CLINIC_OPERATION, token)).hasStatus(HttpStatus.OK);
    }

    @Test
    void permissionsRemovedFromTheRolesApplyToTheNextRequest() {
        String email = IdentityApi.uniqueEmail("owner");
        String password = IdentityApi.randomPassword();
        UUID tenantId = api.signUp("Clinica Revocada", email, password);
        String token = api.token(email, password);

        jdbc.update("delete from role_permission where tenant_id = ? and permission_code = 'roles.read'", tenantId);

        assertThat(api.get(CLINIC_OPERATION, token)).hasStatus(HttpStatus.FORBIDDEN);
    }

    /** A second platform account (the bootstrap one belongs to BootstrapAdminIT), already active. */
    private String platformAdminToken() {
        String email = IdentityApi.uniqueEmail("platform");
        String password = IdentityApi.randomPassword();
        jdbc.update("""
                insert into user_account (id, email, password_hash, display_name, platform_admin, must_change_password)
                values (?, ?, ?, 'Kuador', true, false)""",
                UUID.randomUUID(), email, passwordEncoder.encode(password));
        return api.token(email, password);
    }
}
