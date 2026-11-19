package com.kuador.medical.identity;

import static com.kuador.medical.identity.StaffApi.allBranches;
import static com.kuador.medical.identity.StaffApi.id;
import static com.kuador.medical.identity.StaffApi.newStaff;
import static com.kuador.medical.identity.StaffApi.perBranch;
import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.AdjustablePlanLimits;
import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 * {@code /staff-users} (MEDK-16): invitation of staff users, email unique per clinic, clinical
 * roles with professional registration, plan user limit, idempotent creation, edition,
 * disable/enable and isolation between clinics (rules 3, 5, 6 and 7 of the plan of MEDK-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class StaffUserIT {

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
    private AdjustablePlanLimits planLimits;

    private final ContractSchema contract = ContractSchema.userRegistration();
    private IdentityApi api;
    private StaffApi staffApi;
    private StaffFixture staff;
    private Clinic clinic;
    private UUID main;
    private UUID north;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        staffApi = new StaffApi(mvc, jsonMapper, mail);
        staff = new StaffFixture(jdbc, passwordEncoder);
        clinic = api.clinic("Clinica Personal");
        main = staff.branch(clinic.tenantId(), "Matriz");
        north = staff.branch(clinic.tenantId(), "Norte");
    }

    @Test
    void ownerRegistersAStaffUserThatIsInvitedByEmail() {
        String email = IdentityApi.uniqueEmail("staff");

        MvcTestResult result = staffApi.create(clinic.token(), newStaff(email, allBranches(reception())));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        Map<String, Object> body = api.body(result);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/staff-users/" + id(body));
        assertThat(body).containsEntry("status", "INVITED").containsEntry("email", email).containsEntry("version", 0);
        assertThat((List<?>) body.get("roleAssignments")).hasSize(2);
        Instant expiresAt = Instant.parse(body.get("invitationExpiresAt").toString());
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofHours(71), Duration.ofHours(72));
        contract.assertResponse("/staff-users", "POST", 201, "application/json", body);

        assertThat(jdbc.queryForObject("select password_hash from identity_user_account where id = ?", String.class, id(body)))
                .isNull();
        assertThat(staffApi.emailsTo(email)).singleElement().satisfies(message -> {
            assertThat(message.subject()).contains("Clinica Personal");
            assertThat(message.body()).contains("/invitation#t=").doesNotContain("?t=");
        });
        assertThat(audited(id(body))).contains("STAFF_USER_CREATED", "INVITATION_SENT", "ROLE_ASSIGNMENTS_REPLACED");
    }

    @Test
    void clinicalRoleRequiresAProfessionalRegistration() {
        String email = IdentityApi.uniqueEmail("doctor");
        Map<String, Object> request = newStaff(email, perBranch(main, staff.role(clinic.tenantId(), "DOCTOR")));

        assertThat(staffApi.create(clinic.token(), request))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"))
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("professionalRegistration"))
                .hasPathSatisfying("$.errors[0].code", code -> assertThat(code).asString().isEqualTo("REQUIRED"));
        assertThat(countAccounts(email)).isZero();
        assertThat(staffApi.emailsTo(email)).isEmpty();

        request.put("professionalRegistration", "REG-TEST-0001");
        assertThat(staffApi.create(clinic.token(), request)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void clinicalRoleCannotBeAssignedLaterToAUserWithoutRegistration() {
        UUID user = id(api.body(staffApi.create(clinic.token(), newStaff(IdentityApi.uniqueEmail("staff"), perBranch(main, reception())))));

        assertThat(api.put("/api/v1/staff-users/" + user + "/role-assignments", clinic.token(),
                perBranch(main, staff.role(clinic.tenantId(), "DOCTOR"))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VALIDATION"));
    }

    @Test
    void emailIsUniqueInTheClinicButNotAcrossClinics() {
        String email = IdentityApi.uniqueEmail("shared");
        assertThat(staffApi.create(clinic.token(), newStaff(email, allBranches(reception())))).hasStatus(HttpStatus.CREATED);

        assertThat(staffApi.create(clinic.token(), newStaff(email.toUpperCase(), allBranches(reception()))))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("EMAIL_TAKEN"));

        Clinic other = api.clinic("Clinica Otra");
        UUID otherRole = staff.role(other.tenantId(), "RECEPTION");
        staff.branch(other.tenantId(), "Matriz");
        assertThat(staffApi.create(other.token(), newStaff(email, allBranches(otherRole)))).hasStatus(HttpStatus.CREATED);
        assertThat(countAccounts(email)).isEqualTo(2);
    }

    @Test
    void onlyCallersWithUsersManageRegisterStaff() {
        String email = IdentityApi.uniqueEmail("viewer");
        String password = IdentityApi.randomPassword();
        UUID viewer = staff.staffUser(clinic.tenantId(), email, password);
        UUID viewerRole = customRole("Consulta de personal", List.of("identity.users.read"));
        staff.assign(clinic.tenantId(), viewer, null, viewerRole);
        String token = api.token(email, password);

        assertThat(staffApi.list(token, "")).hasStatus(HttpStatus.OK);
        String invited = IdentityApi.uniqueEmail("staff");
        assertThat(staffApi.create(token, newStaff(invited, allBranches(reception()))))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("FORBIDDEN"));
        UUID target = staff.staffUser(clinic.tenantId());
        assertThat(staffApi.action(token, target, "disable")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(staffApi.action(token, target, "invitation")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(staffApi.patch(token, target, Map.of("jobTitle", "Other", "version", 0))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(countAccounts(invited)).isZero();
    }

    @Test
    void planUserLimitIsCheckedWhenInvitingAndEnabling() {
        planLimits.limitUsers(clinic.tenantId(), 2);
        UUID first = id(api.body(staffApi.create(clinic.token(), newStaff(IdentityApi.uniqueEmail("one"), allBranches(reception())))));

        String overLimit = IdentityApi.uniqueEmail("two");
        assertThat(staffApi.create(clinic.token(), newStaff(overLimit, allBranches(reception()))))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("PLAN_LIMIT_REACHED"));
        assertThat(countAccounts(overLimit)).isZero();

        assertThat(staffApi.action(clinic.token(), first, "disable")).hasStatus(HttpStatus.OK);
        assertThat(staffApi.create(clinic.token(), newStaff(overLimit, allBranches(reception())))).hasStatus(HttpStatus.CREATED);
        assertThat(staffApi.action(clinic.token(), first, "enable"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("PLAN_LIMIT_REACHED"));
    }

    @Test
    void sameIdempotencyKeyDoesNotCreateADuplicate() {
        String email = IdentityApi.uniqueEmail("retry");
        Map<String, Object> request = newStaff(email, allBranches(reception()));
        String key = UUID.randomUUID().toString();

        MvcTestResult first = staffApi.create(clinic.token(), request, key);
        MvcTestResult retry = staffApi.create(clinic.token(), request, key);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(id(api.body(retry))).isEqualTo(id(api.body(first)));
        assertThat(countAccounts(email)).isOne();
        assertThat(staffApi.emailsTo(email)).hasSize(1);

        Map<String, Object> different = newStaff(IdentityApi.uniqueEmail("other"), allBranches(reception()));
        assertThat(staffApi.create(clinic.token(), different, key))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void idempotencyKeysAreKeptPerClinic() {
        String key = UUID.randomUUID().toString();
        Clinic other = api.clinic("Clinica Clave");
        staff.branch(other.tenantId(), "Matriz");

        UUID mine = id(api.body(staffApi.create(clinic.token(), newStaff(IdentityApi.uniqueEmail("a"), allBranches(reception())), key)));
        MvcTestResult theirs = staffApi.create(other.token(),
                newStaff(IdentityApi.uniqueEmail("b"), allBranches(staff.role(other.tenantId(), "RECEPTION"))), key);

        assertThat(theirs).hasStatus(HttpStatus.CREATED);
        assertThat(id(api.body(theirs))).isNotEqualTo(mine);
    }

    @Test
    void clientGeneratedIdIsUsedOnceOnly() {
        UUID clientId = UUID.randomUUID();
        Map<String, Object> request = newStaff(IdentityApi.uniqueEmail("offline"), allBranches(reception()));
        request.put("id", clientId.toString());

        assertThat(id(api.body(staffApi.create(clinic.token(), request)))).isEqualTo(clientId);

        Map<String, Object> again = newStaff(IdentityApi.uniqueEmail("offline"), allBranches(reception()));
        again.put("id", clientId.toString());
        assertThat(staffApi.create(clinic.token(), again))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("RESOURCE_ID_TAKEN"));
    }

    @Test
    void listsFiltersAndGetsStaffUsers() {
        String email = IdentityApi.uniqueEmail("findme");
        Map<String, Object> request = newStaff(email, perBranch(north, reception()));
        request.put("lastNames", "Zamora Unica");
        UUID user = id(api.body(staffApi.create(clinic.token(), request)));

        Map<String, Object> page = api.body(staffApi.list(clinic.token(), "?q=zamora&size=5"));
        assertThat(page).containsEntry("page", 0).containsEntry("size", 5).containsEntry("totalItems", 1);
        assertThat((List<Map<String, Object>>) page.get("items")).singleElement()
                .satisfies(item -> assertThat(item.get("id")).isEqualTo(user.toString()));
        // StaffUserPage is an allOf, which ContractSchema does not resolve: items are StaffUser.
        assertThat(page).containsOnlyKeys("page", "size", "totalItems", "items");
        ((List<Map<String, Object>>) page.get("items"))
                .forEach(item -> contract.assertResponse("/staff-users/{id}", "GET", 200, "application/json", item));

        assertThat(totalItems("?status=INVITED&q=zamora")).isOne();
        assertThat(totalItems("?status=ACTIVE&q=zamora")).isZero();
        assertThat(totalItems("?branchId=" + north + "&q=zamora")).isOne();
        assertThat(totalItems("?branchId=" + main + "&q=zamora")).isZero();
        assertThat(totalItems("")).isEqualTo(2);

        MvcTestResult found = staffApi.get(clinic.token(), user);
        assertThat(found).hasStatus(HttpStatus.OK);
        contract.assertResponse("/staff-users/{id}", "GET", 200, "application/json", api.body(found));
    }

    @Test
    void updatesPersonalDataWithOptimisticLocking() {
        UUID doctor = id(api.body(staffApi.create(clinic.token(), withRegistration(
                newStaff(IdentityApi.uniqueEmail("doc"), perBranch(main, staff.role(clinic.tenantId(), "DOCTOR")))))));

        MvcTestResult updated = staffApi.patch(clinic.token(), doctor, Map.of("jobTitle", "Head of medicine", "version", 0));
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(api.body(updated)).containsEntry("jobTitle", "Head of medicine").containsEntry("version", 1)
                .containsEntry("lastNames", "Doe");
        contract.assertResponse("/staff-users/{id}", "PATCH", 200, "application/json", api.body(updated));

        assertThat(staffApi.patch(clinic.token(), doctor, Map.of("jobTitle", "Stale", "version", 0)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("VERSION_CONFLICT"));

        Map<String, Object> removeRegistration = new LinkedHashMap<>();
        removeRegistration.put("professionalRegistration", null);
        removeRegistration.put("version", 1);
        assertThat(staffApi.patch(clinic.token(), doctor, removeRegistration))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.errors[0].field", field -> assertThat(field).asString().isEqualTo("professionalRegistration"));

        assertThat(staffApi.patch(clinic.token(), doctor, Map.of("version", 1)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(audited(doctor)).contains("STAFF_USER_UPDATED");
    }

    @Test
    void nobodyEditsNorDisablesThemselves() {
        UUID owner = staff.ownerOf(clinic.tenantId());

        for (MvcTestResult result : List.of(
                staffApi.patch(clinic.token(), owner, Map.of("jobTitle", "Self", "version", 0)),
                staffApi.action(clinic.token(), owner, "disable"),
                staffApi.action(clinic.token(), owner, "enable"))) {
            assertThat(result)
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("SELF_MODIFICATION_FORBIDDEN"));
        }
    }

    @Test
    void disablingDeniesAccessAtOnceAndEnablingRestoresIt() {
        String email = IdentityApi.uniqueEmail("active");
        String password = IdentityApi.randomPassword();
        UUID user = staff.staffUser(clinic.tenantId(), email, password);
        String token = api.token(email, password);

        MvcTestResult disabled = staffApi.action(clinic.token(), user, "disable");

        assertThat(disabled).hasStatus(HttpStatus.OK);
        assertThat(api.body(disabled)).containsEntry("status", "DISABLED");
        contract.assertResponse("/staff-users/{id}/disable", "POST", 200, "application/json", api.body(disabled));
        assertThat(api.me(token)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.login(email, password)).hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult enabled = staffApi.action(clinic.token(), user, "enable");
        assertThat(enabled).hasStatus(HttpStatus.OK);
        assertThat(api.body(enabled)).containsEntry("status", "ACTIVE");
        assertThat(api.login(email, password)).hasStatus(HttpStatus.OK);
        assertThat(audited(user)).contains("STAFF_USER_DISABLED", "STAFF_USER_ENABLED");
    }

    @Test
    void onlyAnOwnerDisablesAnOwner() {
        String email = IdentityApi.uniqueEmail("admin");
        String password = IdentityApi.randomPassword();
        UUID admin = staff.staffUser(clinic.tenantId(), email, password);
        staff.assign(clinic.tenantId(), admin, null, staff.role(clinic.tenantId(), "ADMIN"));

        assertThat(staffApi.action(api.token(email, password), staff.ownerOf(clinic.tenantId()), "disable"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("PRIVILEGE_ESCALATION"));
    }

    @Test
    void resendingInvalidatesThePreviousInvitation() {
        String email = IdentityApi.uniqueEmail("resend");
        UUID user = id(api.body(staffApi.create(clinic.token(), newStaff(email, allBranches(reception())))));
        String first = staffApi.lastInvitationToken(email);

        assertThat(staffApi.action(clinic.token(), user, "invitation")).hasStatus(HttpStatus.ACCEPTED);

        String second = staffApi.lastInvitationToken(email);
        assertThat(second).isNotEqualTo(first);
        assertThat(staffApi.preview(first)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(staffApi.preview(second)).hasStatus(HttpStatus.OK);

        assertThat(staffApi.accept(second, IdentityApi.randomPassword())).hasStatus(HttpStatus.OK);
        assertThat(staffApi.action(clinic.token(), user, "invitation"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> assertThat(code).asString().isEqualTo("ACCOUNT_ALREADY_ACTIVE"));
    }

    @Test
    void staffOfAnotherClinicIsInvisible() {
        Clinic other = api.clinic("Clinica Ajena");
        staff.branch(other.tenantId(), "Matriz");
        String foreignEmail = IdentityApi.uniqueEmail("foreign");
        UUID foreign = id(api.body(staffApi.create(other.token(),
                newStaff(foreignEmail, allBranches(staff.role(other.tenantId(), "RECEPTION"))))));

        assertThat(staffApi.get(clinic.token(), foreign)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(staffApi.patch(clinic.token(), foreign, Map.of("jobTitle", "Hacked", "version", 0)))
                .hasStatus(HttpStatus.NOT_FOUND);
        for (String action : List.of("disable", "enable", "invitation")) {
            assertThat(staffApi.action(clinic.token(), foreign, action)).as(action).hasStatus(HttpStatus.NOT_FOUND);
        }
        assertThat(api.put("/api/v1/staff-users/" + foreign + "/role-assignments", clinic.token(), allBranches(reception())))
                .hasStatus(HttpStatus.NOT_FOUND);
        Map<String, Object> page = api.body(staffApi.list(clinic.token(), "?size=100"));
        assertThat((List<Map<String, Object>>) page.get("items"))
                .extracting(item -> item.get("id"))
                .doesNotContain(foreign.toString());
        assertThat(totalItems("?q=" + foreignEmail.substring(0, 20))).isZero();

        assertThat(jdbc.queryForObject("select status from identity_user_account where id = ?", String.class, foreign))
                .isEqualTo("INVITED");
        assertThat(staffApi.emailsTo(foreignEmail)).hasSize(1);
    }

    private UUID reception() {
        return staff.role(clinic.tenantId(), "RECEPTION");
    }

    private UUID customRole(String name, List<String> permissions) {
        MvcTestResult created = api.post("/api/v1/roles", clinic.token(), Map.of("name", name, "permissions", permissions));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return id(api.body(created));
    }

    private static Map<String, Object> withRegistration(Map<String, Object> request) {
        request.put("professionalRegistration", "REG-TEST-0002");
        return request;
    }

    private long totalItems(String query) {
        return ((Number) api.body(staffApi.list(clinic.token(), query)).get("totalItems")).longValue();
    }

    private int countAccounts(String email) {
        return jdbc.queryForObject("select count(*) from identity_user_account where email_normalized = ?",
                Integer.class, email.toLowerCase());
    }

    private List<String> audited(UUID target) {
        return jdbc.queryForList("select action from audit_event where tenant_id = ? and target_id = ? order by occurred_at",
                String.class, clinic.tenantId(), target);
    }
}
