package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Public API {@link AccountProvisioning} used by the patients module (MEDK-16): invited accounts
 * of patients and guardians, unique email per clinic and isolation between clinics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountProvisioningIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private AccountProvisioning provisioning;

    @Autowired
    private CapturingNotificationSender mail;

    private IdentityApi api;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        tenantId = api.signUp("Clinica Pacientes", IdentityApi.uniqueEmail("owner"), IdentityApi.randomPassword());
    }

    @Test
    void invitesAPatientAccountThatCanBeAccepted() {
        String email = IdentityApi.uniqueEmail("patient");
        UUID patientRef = UUID.randomUUID();

        UUID accountId = inClinic(tenantId, () -> provisioning.inviteAccount(
                new NewClinicAccount(AccountKind.PATIENT, email, "John", "Roe", patientRef)));

        assertThat(jdbc.queryForMap("select tenant_id, kind, status, patient_ref from identity_user_account where id = ?", accountId))
                .containsEntry("tenant_id", tenantId)
                .containsEntry("kind", "PATIENT")
                .containsEntry("status", "INVITED")
                .containsEntry("patient_ref", patientRef);
        assertThat(inClinic(tenantId, () -> provisioning.find(accountId)))
                .hasValueSatisfying(account -> {
                    assertThat(account.kind()).isEqualTo(AccountKind.PATIENT);
                    assertThat(account.status()).isEqualTo(ClinicAccountStatus.INVITED);
                });

        StaffApi staffApi = new StaffApi(mvc, jsonMapper, mail);
        String token = staffApi.lastInvitationToken(email);
        assertThat(api.body(staffApi.preview(token))).containsEntry("kind", "PATIENT");
        assertThat(staffApi.accept(token, IdentityApi.randomPassword())).hasStatus(HttpStatus.OK);
        assertThat(inClinic(tenantId, () -> provisioning.find(accountId)).orElseThrow().status())
                .isEqualTo(ClinicAccountStatus.ACTIVE);
    }

    @Test
    void emailIsUniquePerClinic() {
        String email = IdentityApi.uniqueEmail("guardian");
        inClinic(tenantId, () -> provisioning.inviteAccount(new NewClinicAccount(AccountKind.GUARDIAN, email, "Ann", "Roe", null)));

        assertThatThrownBy(() -> inClinic(tenantId, () -> provisioning.inviteAccount(
                new NewClinicAccount(AccountKind.PATIENT, email, "Ann", "Roe", UUID.randomUUID()))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
    }

    @Test
    void staffAccountsAreNotProvisionedThroughThisApi() {
        assertThatThrownBy(() -> inClinic(tenantId, () -> provisioning.inviteAccount(
                new NewClinicAccount(AccountKind.STAFF, IdentityApi.uniqueEmail("x"), "A", "B", null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accountsOfAnotherClinicAreNotFound() {
        UUID other = api.signUp("Clinica Otra", IdentityApi.uniqueEmail("owner"), IdentityApi.randomPassword());
        UUID accountId = inClinic(tenantId, () -> provisioning.inviteAccount(
                new NewClinicAccount(AccountKind.PATIENT, IdentityApi.uniqueEmail("p"), "John", "Roe", UUID.randomUUID())));

        assertThat(inClinic(other, () -> provisioning.find(accountId))).isEmpty();
        assertThatThrownBy(() -> inClinic(other, () -> {
            provisioning.resendInvitation(accountId);
            return null;
        })).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void signupBootstrapsAnActiveOwnerAccount() {
        Map<String, Object> owner = jdbc.queryForMap("""
                select a.kind, a.status from identity_user_account a
                join identity_role_assignment ra on ra.user_id = a.id
                join identity_role r on r.id = ra.role_id
                where a.tenant_id = ? and r.system_code = 'OWNER'""", tenantId);

        assertThat(owner).containsEntry("kind", "STAFF").containsEntry("status", "ACTIVE");
    }

    private <T> T inClinic(UUID tenant, Supplier<T> work) {
        return TenantContext.callAs(tenant, () -> transaction.execute(status -> work.get()));
    }
}
