package com.kuador.medical.platform.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.util.HashMap;
import java.util.Map;
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

/** Platform settings (MEDK-49 plan D10): defaults 14, 7, 90, 90, 72 and 30, editable and audited. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformSettingsIT {

    private static final String SETTINGS = "/api/v1/platform/settings";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformSettingsService service;

    private final ContractSchema contract = ContractSchema.platform();
    private IdentityApi api;
    private Admin admin;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
        restoreDefaults();
    }

    @AfterEach
    void tearDown() {
        restoreDefaults();
    }

    @Test
    void defaultsAreTheDecidedPeriods() {
        MvcTestResult result = api.get(SETTINGS, admin.token());

        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> settings = api.body(result);
        contract.assertResponse("/platform/settings", "GET", 200, "application/json", settings);
        assertThat(settings).containsEntry("trialDays", 14).containsEntry("graceDays", 7)
                .containsEntry("suspensionDaysBeforeCancel", 90).containsEntry("retentionDays", 90)
                .containsEntry("ownerInvitationTtlHours", 72).containsEntry("priceChangeNoticeDays", 30);
        assertThat(service.current().trialDays()).isEqualTo(14);
    }

    @Test
    void superAdminChangesTheSettingsWithOptimisticLockingAndItIsAudited() {
        Map<String, Object> current = api.body(api.get(SETTINGS, admin.token()));
        Map<String, Object> changed = new HashMap<>(current);
        changed.put("graceDays", 5);
        changed.put("ownerInvitationTtlHours", 48);

        MvcTestResult updated = api.put(SETTINGS, admin.token(), changed);

        assertThat(updated).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(updated);
        contract.assertResponse("/platform/settings", "PUT", 200, "application/json", body);
        assertThat(body).containsEntry("graceDays", 5).containsEntry("ownerInvitationTtlHours", 48)
                .containsEntry("trialDays", 14)
                .containsEntry("version", ((Number) current.get("version")).intValue() + 1);
        assertThat(service.current().graceDays()).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event
                where action = 'PLATFORM_SETTINGS_UPDATED' and actor_account_id = ? and tenant_id is null""",
                Integer.class, admin.accountId())).isOne();

        assertThat(api.put(SETTINGS, admin.token(), changed))
                .as("the version read before the change is stale")
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VERSION_CONFLICT"));
    }

    @Test
    void valuesOutOfRangeAreRejected() {
        Map<String, Object> invalid = new HashMap<>(api.body(api.get(SETTINGS, admin.token())));
        invalid.put("trialDays", 0);
        invalid.remove("retentionDays");

        MvcTestResult result = api.put(SETTINGS, admin.token(), invalid);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = api.body(result);
        contract.assertResponse("/platform/settings", "PUT", 400, "application/problem+json", body);
        assertThat(body.get("errors").toString()).contains("trialDays", "retentionDays");
        assertThat(service.current().trialDays()).isEqualTo(14);
    }

    private void restoreDefaults() {
        jdbc.update("""
                update platform_setting set value = case key
                    when 'TRIAL_DAYS' then 14 when 'GRACE_DAYS' then 7 when 'SUSPENSION_DAYS_BEFORE_CANCEL' then 90
                    when 'RETENTION_DAYS' then 90 when 'OWNER_INVITATION_TTL_HOURS' then 72
                    when 'PRICE_CHANGE_NOTICE_DAYS' then 30 end""");
    }
}
