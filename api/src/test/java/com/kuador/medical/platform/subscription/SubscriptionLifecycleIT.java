package com.kuador.medical.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.ContractSchema;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.IdentityApi.Clinic;
import com.kuador.medical.TestcontainersConfiguration;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.platform.PlatformAdminFixture;
import com.kuador.medical.platform.PlatformAdminFixture.Admin;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Subscription lifecycle and reactivation (MEDK-68; MEDK-49 CA 3, 10, 11, 12, 13, 14 and 16,
 * plan D1, D5 and D12): every transition of the table through its real trigger (HTTP, payment
 * event or job), invalid transitions, the reactivation window, a job run twice, price change
 * notices and price lock, audit, emails to the OWNER, isolation and no personal data in logs.
 * Deadlines are reached by moving the clinic's dates into the past, so the job runs at the real
 * time and only the clinics of the test are due.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@RecordApplicationEvents
class SubscriptionLifecycleIT {

    private static final String TENANTS = "/api/v1/platform/tenants";
    private static final String TRIAL_PLAN_ID = "6b5a4c3d-2e1f-4a0b-8c9d-7e6f5a4b3c2d";
    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");
    private static final String SUBSCRIPTION = "/platform/tenants/{tenantId}/subscription";
    private static final String TRANSITIONS = SUBSCRIPTION + "/transitions";
    private static final String PRICE_LOCK = SUBSCRIPTION + "/price-lock";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubscriptionLifecycleJob job;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private CapturingNotificationSender emails;

    @Autowired
    private ApplicationEvents events;

    private final ContractSchema contract = ContractSchema.platform();
    private IdentityApi api;
    private Admin admin;

    @BeforeEach
    void setUp() {
        api = new IdentityApi(mvc, jsonMapper);
        admin = PlatformAdminFixture.create(api, jdbc, passwordEncoder);
    }

    // ------------------------------------------------------------------ read

    @Test
    void theSuperAdminReadsTheSubscriptionWithItsHistoryAndAllowedTargets() {
        Onboarded clinic = onboard(TRIAL_PLAN_ID);

        Map<String, Object> subscription = getSubscription(clinic.id());

        assertThat(subscription).containsEntry("status", "TRIAL").containsEntry("accessMode", "FULL")
                .containsEntry("priceAmount", null).containsEntry("currency", "USD").containsEntry("purged", false)
                .containsEntry("reactivable", false).containsEntry("scheduledPlanChange", null)
                .containsEntry("version", 0);
        assertThat(list(subscription.get("allowedTargets"))).containsExactly("ACTIVE", "CANCELLED");
        assertThat(cast(subscription.get("plan"))).containsEntry("id", TRIAL_PLAN_ID);
        assertThat(maps(subscription.get("transitions"))).singleElement().satisfies(initial -> assertThat(initial)
                .containsEntry("from", null).containsEntry("to", "TRIAL").containsEntry("cause", "MANUAL")
                .containsEntry("actor", "Kuador"));
        assertThat(getRaw(UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ job (CA 10, 11, 12)

    @Test
    void aTrialThatEndsWithoutPaymentIsSuspendedAndAJobRunTwiceChangesNothingMore() {
        Onboarded clinic = onboard(TRIAL_PLAN_ID);
        Instant trialEnd = startOfDay(LocalDate.now(GUAYAQUIL).minusDays(1));
        jdbc.update("update subscription set trial_ends_at = ? where tenant_id = ?", Timestamp.from(trialEnd), clinic.id());

        CompletableFuture<SubscriptionLifecycleJob.Result> first = CompletableFuture.supplyAsync(() -> job.runOnce(Instant.now()));
        CompletableFuture<SubscriptionLifecycleJob.Result> second = CompletableFuture.supplyAsync(() -> job.runOnce(Instant.now()));
        first.join();
        second.join();
        job.runOnce(Instant.now());

        Map<String, Object> subscription = getSubscription(clinic.id());
        assertThat(subscription).containsEntry("status", "SUSPENDED").containsEntry("accessMode", "READ_ONLY");
        assertThat(Instant.parse(subscription.get("suspendedAt").toString())).isEqualTo(trialEnd);
        assertThat(maps(subscription.get("transitions")).stream().filter(t -> "SCHEDULER".equals(t.get("cause"))))
                .singleElement().satisfies(t -> assertThat(t).containsEntry("from", "TRIAL")
                .containsEntry("to", "SUSPENDED").containsEntry("actor", null));
        assertThat(transitions(clinic.id(), "SCHEDULER")).as("concurrent and repeated runs apply it once").isOne();
        assertThat(audited(clinic.id(), "SUBSCRIPTION_STATUS_CHANGED", null)).isOne();
        assertThat(emails.awaitSentTo(clinic.ownerEmail(), 1)).singleElement()
                .satisfies(email -> assertThat(email.subject()).contains("suspendida"));
    }

    @Test
    void anActiveSubscriptionUnpaidAtTheEndOfItsPeriodIsPastDueWithFullAccessAndANotice() {
        Onboarded clinic = onboard(createPaidPlan());
        Instant periodEnd = startOfDay(LocalDate.now(GUAYAQUIL).minusDays(1));
        jdbc.update("update subscription set current_period_end = ? where tenant_id = ?", Timestamp.from(periodEnd),
                clinic.id());

        job.runOnce(Instant.now());

        Map<String, Object> subscription = getSubscription(clinic.id());
        assertThat(subscription).containsEntry("status", "PAST_DUE").containsEntry("accessMode", "FULL");
        assertThat(Instant.parse(subscription.get("graceEndsAt").toString()))
                .isEqualTo(startOfDay(LocalDate.now(GUAYAQUIL).minusDays(1).plusDays(setting("GRACE_DAYS"))));
        assertThat(emails.awaitSentTo(clinic.ownerEmail(), 1)).singleElement()
                .satisfies(email -> assertThat(email.subject()).contains("vencida"));
    }

    @Test
    void afterTheGracePeriodItIsSuspendedAndAfterTheSuspensionPeriodCancelled() {
        Onboarded clinic = onboard(createPaidPlan());
        int grace = setting("GRACE_DAYS");
        int suspension = setting("SUSPENSION_DAYS_BEFORE_CANCEL");
        LocalDate periodEnd = LocalDate.now(GUAYAQUIL).minusDays(grace + suspension + 2L);
        jdbc.update("update subscription set current_period_end = ? where tenant_id = ?",
                Timestamp.from(startOfDay(periodEnd)), clinic.id());

        job.runOnce(Instant.now());

        Map<String, Object> subscription = getSubscription(clinic.id());
        LocalDate suspendedOn = periodEnd.plusDays(grace);
        LocalDate cancelledOn = suspendedOn.plusDays(suspension);
        assertThat(subscription).containsEntry("status", "CANCELLED").containsEntry("accessMode", "RETENTION")
                .containsEntry("reactivable", true);
        assertThat(list(subscription.get("allowedTargets"))).containsExactly("ACTIVE");
        assertThat(Instant.parse(subscription.get("cancelledAt").toString())).isEqualTo(startOfDay(cancelledOn));
        assertThat(Instant.parse(subscription.get("retentionEndsAt").toString()))
                .isEqualTo(startOfDay(cancelledOn.plusDays(setting("RETENTION_DAYS"))));
        assertThat(maps(subscription.get("transitions")).stream().filter(t -> "SCHEDULER".equals(t.get("cause")))
                .map(t -> t.get("from") + ">" + t.get("to")).toList())
                .as("newest first, each at its own deadline")
                .containsExactly("SUSPENDED>CANCELLED", "PAST_DUE>SUSPENDED", "ACTIVE>PAST_DUE");
        assertThat(job.runOnce(Instant.now()).executed()).isTrue();
        assertThat(transitions(clinic.id(), "SCHEDULER")).isEqualTo(3);
        contract.assertResponse(SUBSCRIPTION, "GET", 200, "application/json", subscription);
    }

    // ------------------------------------------------------------------ manual transitions

    @Test
    void theSuperAdminSuspendsAndReactivatesASuspendedClinicWithItsDataIntact() {
        Onboarded clinic = onboard(createPaidPlan());

        MvcTestResult suspended = transition(clinic.id(), "SUSPENDED", "Pedido del cliente", 0);

        assertThat(suspended).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(suspended);
        contract.assertResponse(TRANSITIONS, "POST", 200, "application/json", body);
        assertThat(body).containsEntry("status", "SUSPENDED").containsEntry("accessMode", "READ_ONLY");
        assertThat(maps(body.get("transitions")).getFirst()).containsEntry("cause", "MANUAL")
                .containsEntry("actor", "Kuador").containsEntry("reason", "Pedido del cliente");

        MvcTestResult reactivated = transition(clinic.id(), "ACTIVE", null, version(body));

        assertThat(reactivated).hasStatus(HttpStatus.OK);
        Map<String, Object> active = api.body(reactivated);
        assertThat(active).containsEntry("status", "ACTIVE").containsEntry("suspendedAt", null);
        assertThat(active.get("reactivatedAt")).isNotNull();
        assertThat(Instant.parse(active.get("currentPeriodStart").toString()))
                .isEqualTo(startOfDay(LocalDate.now(GUAYAQUIL)));
        assertThat(audited(clinic.id(), "SUBSCRIPTION_STATUS_CHANGED", admin.accountId())).isEqualTo(2);
        assertThat(emails.awaitSentTo(clinic.ownerEmail(), 2)).extracting(EmailMessage::subject)
                .anySatisfy(subject -> assertThat(subject).contains("reactivada"));
        assertThat(events.stream(SubscriptionStatusChanged.class)
                .filter(event -> event.tenantId().equals(clinic.id()))).hasSize(2);
    }

    @Test
    void transitionsOutsideTheTableAndStaleVersionsAreRejected() {
        Onboarded clinic = onboard(TRIAL_PLAN_ID);

        assertCode(transition(clinic.id(), "PAST_DUE", null, 0), HttpStatus.CONFLICT, "INVALID_SUBSCRIPTION_TRANSITION");
        assertCode(transition(clinic.id(), "SUSPENDED", null, 0), HttpStatus.CONFLICT, "INVALID_SUBSCRIPTION_TRANSITION");
        assertCode(transition(clinic.id(), "TRIAL", null, 0), HttpStatus.CONFLICT, "INVALID_SUBSCRIPTION_TRANSITION");
        MvcTestResult noPrice = transition(clinic.id(), "ACTIVE", null, 0);
        assertCode(noPrice, HttpStatus.CONFLICT, "INVALID_SUBSCRIPTION_TRANSITION");
        contract.assertResponse(TRANSITIONS, "POST", 409, "application/problem+json", api.body(noPrice));
        assertCode(transition(clinic.id(), "CANCELLED", null, 7), HttpStatus.CONFLICT, "VERSION_CONFLICT");
        assertCode(transition(UUID.randomUUID(), "CANCELLED", null, 0), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertCode(api.post(TENANTS + "/" + clinic.id() + "/subscription/transitions", admin.token(),
                Map.of("version", 0)), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertThat(getSubscription(clinic.id())).containsEntry("status", "TRIAL").containsEntry("version", 0);
        assertThat(transitions(clinic.id(), "MANUAL")).as("only the initial status").isOne();
    }

    // ------------------------------------------------------------------ reactivation (CA 13, 14, D12)

    @Test
    void aCancelledClinicIsReactivatedWithReasonWithinTheRetentionAtTheCurrentPrice() {
        String planId = createPaidPlan();
        Onboarded clinic = onboard(planId);
        Map<String, Object> cancelled = api.body(transition(clinic.id(), "CANCELLED", "Baja pedida por el cliente", 0));
        assertThat(cancelled).containsEntry("status", "CANCELLED").containsEntry("reactivable", true);
        createPrice(planId, "MONTHLY", "55.00", Instant.now());

        assertCode(transition(clinic.id(), "ACTIVE", null, version(cancelled)), HttpStatus.BAD_REQUEST, "VALIDATION");
        assertCode(transition(clinic.id(), "ACTIVE", "   ", version(cancelled)), HttpStatus.BAD_REQUEST, "VALIDATION");
        MvcTestResult reactivated = transition(clinic.id(), "ACTIVE", "Pagó el saldo por transferencia", version(cancelled));

        assertThat(reactivated).hasStatus(HttpStatus.OK);
        Map<String, Object> active = api.body(reactivated);
        assertThat(active).containsEntry("status", "ACTIVE").containsEntry("priceAmount", "55.00")
                .containsEntry("cancelledAt", null).containsEntry("retentionEndsAt", null).containsEntry("reactivable", false);
        assertThat(Instant.parse(active.get("currentPeriodStart").toString()))
                .as("a new period from the reactivation").isEqualTo(startOfDay(LocalDate.now(GUAYAQUIL)));
        assertThat(maps(active.get("periods")).getFirst()).containsEntry("amount", "55.00");
        assertThat(maps(active.get("transitions")).getFirst()).containsEntry("from", "CANCELLED")
                .containsEntry("to", "ACTIVE").containsEntry("cause", "MANUAL")
                .containsEntry("reason", "Pagó el saldo por transferencia").containsEntry("actor", "Kuador");
        assertThat(events.stream(SubscriptionStatusChanged.class)
                .filter(event -> event.tenantId().equals(clinic.id()) && event.from() == SubscriptionStatus.CANCELLED))
                .singleElement().satisfies(event -> assertThat(event.cause()).isEqualTo(TransitionCause.MANUAL));
        assertThat(audited(clinic.id(), "SUBSCRIPTION_STATUS_CHANGED", admin.accountId())).isEqualTo(2);
    }

    @Test
    void aCancelledClinicIsNotReactivatedOutsideTheRetentionWhenPurgedOrByAPayment() {
        Onboarded clinic = onboard(createPaidPlan());
        Map<String, Object> cancelled = api.body(transition(clinic.id(), "CANCELLED", null, 0));
        long version = version(cancelled);

        publisher.publishEvent(new PaymentReceived(clinic.id(), "pay-" + UUID.randomUUID(), new BigDecimal("49.00"),
                "USD", Instant.now()));
        assertThat(getSubscription(clinic.id())).containsEntry("status", "CANCELLED").containsEntry("version", (int) version);
        assertThat(audited(clinic.id(), "SUBSCRIPTION_PAYMENT_REJECTED", null)).isOne();

        Clinic other = api.clinic("Clinica Ajena " + UUID.randomUUID().toString().substring(0, 8));
        assertThat(api.post(TENANTS + "/" + clinic.id() + "/subscription/transitions", other.token(),
                Map.of("target", "ACTIVE", "reason", "Intento", "version", version))).hasStatus(HttpStatus.FORBIDDEN);

        jdbc.update("update subscription set retention_ends_at = ? where tenant_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), clinic.id());
        assertThat(getSubscription(clinic.id())).containsEntry("reactivable", false);
        assertThat(list(getSubscription(clinic.id()).get("allowedTargets"))).isEmpty();
        MvcTestResult closed = transition(clinic.id(), "ACTIVE", "Fuera de plazo", version);
        assertCode(closed, HttpStatus.CONFLICT, "REACTIVATION_WINDOW_CLOSED");
        contract.assertResponse(TRANSITIONS, "POST", 409, "application/problem+json", api.body(closed));

        jdbc.update("update subscription set retention_ends_at = ? where tenant_id = ?",
                Timestamp.from(Instant.now().plus(Duration.ofDays(30))), clinic.id());
        jdbc.update("update tenant set purged_at = now() where id = ?", clinic.id());
        assertThat(getSubscription(clinic.id())).containsEntry("purged", true).containsEntry("accessMode", "NONE")
                .containsEntry("reactivable", false);
        assertCode(transition(clinic.id(), "ACTIVE", "Ya purgada", version), HttpStatus.CONFLICT,
                "REACTIVATION_WINDOW_CLOSED");
        assertThat(jdbc.queryForObject("select status from subscription where tenant_id = ?", String.class, clinic.id()))
                .isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------ payment events (CA 16, D5)

    @Test
    void paymentEventsActivateRenewAndAreAppliedOnce() {
        Onboarded clinic = onboard(createPaidPlan());
        Instant periodEnd = startOfDay(LocalDate.now(GUAYAQUIL).minusDays(1));
        jdbc.update("update subscription set current_period_end = ? where tenant_id = ?", Timestamp.from(periodEnd),
                clinic.id());
        job.runOnce(Instant.now());
        assertThat(getSubscription(clinic.id())).containsEntry("status", "PAST_DUE");

        publisher.publishEvent(new PaymentFailed(clinic.id(), "fail-1", Instant.now()));
        assertThat(getSubscription(clinic.id())).as("a failed payment never changes the status (D5)")
                .containsEntry("status", "PAST_DUE");
        assertThat(audited(clinic.id(), "SUBSCRIPTION_PAYMENT_FAILED", null)).isOne();

        String reference = "pay-" + UUID.randomUUID();
        PaymentReceived payment = new PaymentReceived(clinic.id(), reference, new BigDecimal("49.00"), "USD", Instant.now());
        publisher.publishEvent(payment);
        publisher.publishEvent(payment);

        Map<String, Object> active = getSubscription(clinic.id());
        assertThat(active).containsEntry("status", "ACTIVE").containsEntry("graceEndsAt", null);
        assertThat(Instant.parse(active.get("currentPeriodStart").toString()))
                .as("the overdue period continues from the end of the previous one").isEqualTo(periodEnd);
        assertThat(transitions(clinic.id(), "PAYMENT_RECEIVED")).as("a delivered payment applies once").isOne();

        publisher.publishEvent(new PaymentReceived(clinic.id(), "pay-" + UUID.randomUUID(), new BigDecimal("49.00"),
                "USD", Instant.now()));
        Map<String, Object> renewed = getSubscription(clinic.id());
        assertThat(renewed).containsEntry("status", "ACTIVE");
        assertThat(Instant.parse(renewed.get("currentPeriodStart").toString()))
                .isEqualTo(Instant.parse(active.get("currentPeriodEnd").toString()));
        assertThat(maps(renewed.get("periods"))).hasSize(3).allSatisfy(period -> assertThat(period)
                .containsEntry("amount", "49.00"));
        assertThat(audited(clinic.id(), "SUBSCRIPTION_RENEWED", null)).isOne();
    }

    @Test
    void aPaymentActivatesATrialOnlyWhenItsPlanHasAPrice() {
        Onboarded clinic = onboard(TRIAL_PLAN_ID);

        publisher.publishEvent(new PaymentReceived(clinic.id(), "pay-" + UUID.randomUUID(), BigDecimal.TEN, "USD",
                Instant.now()));

        assertThat(getSubscription(clinic.id())).containsEntry("status", "TRIAL");
        assertThat(audited(clinic.id(), "SUBSCRIPTION_PAYMENT_REJECTED", null)).isOne();

        Onboarded suspended = onboard(TRIAL_PLAN_ID);
        jdbc.update("update subscription set plan_id = ?::uuid where tenant_id = ?", createPaidPlan(), suspended.id());
        publisher.publishEvent(new PaymentReceived(suspended.id(), "pay-" + UUID.randomUUID(), BigDecimal.TEN, "USD",
                Instant.now()));
        assertThat(getSubscription(suspended.id())).containsEntry("status", "ACTIVE").containsEntry("priceAmount", "49.00");
    }

    // ------------------------------------------------------------------ price change notice and lock (CA 3, D1)

    @Test
    void aNewPriceIsNoticedOnceAndAppliesFromTheFirstRenewalAtLeastThirtyDaysLater() {
        String planId = createPaidPlan();
        Onboarded clinic = onboard(planId);
        Instant notifiedAround = Instant.now();

        createPrice(planId, "MONTHLY", "59.00", notifiedAround);
        job.runOnce(Instant.now());

        Map<String, Object> notice = jdbc.queryForMap("""
                select amount::text as amount, applies_from from price_change_notice where tenant_id = ?""", clinic.id());
        Instant appliesFrom = ((Timestamp) notice.get("applies_from")).toInstant();
        assertThat(notice).containsEntry("amount", "59.00");
        assertThat(appliesFrom).isAfterOrEqualTo(startOfDay(LocalDate.now(GUAYAQUIL).plusDays(setting("PRICE_CHANGE_NOTICE_DAYS"))));
        assertThat(emails.awaitSentTo(clinic.ownerEmail(), 1)).singleElement().satisfies(email -> assertThat(email.body())
                .contains("USD 49.00").contains("USD 59.00"));
        assertThat(audited(clinic.id(), "PRICE_CHANGE_NOTICE_SENT", null)).isOne();

        for (int renewal = 0; renewal < 4; renewal++) {
            publisher.publishEvent(new PaymentReceived(clinic.id(), "pay-" + UUID.randomUUID(), BigDecimal.ONE, "USD",
                    Instant.now()));
        }

        List<Map<String, Object>> periods = maps(getSubscription(clinic.id()).get("periods"));
        assertThat(periods).allSatisfy(period -> {
            Instant start = Instant.parse(period.get("periodStart").toString());
            assertThat(period).containsEntry("amount", start.isBefore(appliesFrom) ? "49.00" : "59.00");
        });
        assertThat(periods).anySatisfy(period -> assertThat(period).containsEntry("amount", "59.00"));
        assertThat(jdbc.queryForObject("select count(*) from price_change_notice where tenant_id = ?", Integer.class,
                clinic.id())).isOne();
    }

    @Test
    void aLockedPriceIsKeptUntilTheLastDayOfTheLock() {
        String planId = createPaidPlan();
        Onboarded clinic = onboard(planId);
        LocalDate lockedUntil = LocalDate.now(GUAYAQUIL).plusMonths(4);

        assertCode(api.put(TENANTS + "/" + clinic.id() + "/subscription/price-lock", admin.token(),
                Map.of("lockedUntil", LocalDate.now(GUAYAQUIL).minusDays(1).toString(), "version", 0)),
                HttpStatus.BAD_REQUEST, "VALIDATION");
        MvcTestResult locked = api.put(TENANTS + "/" + clinic.id() + "/subscription/price-lock", admin.token(),
                Map.of("lockedUntil", lockedUntil.toString(), "reason", "Acuerdo de lanzamiento", "version", 0));
        assertThat(locked).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(locked);
        contract.assertResponse(PRICE_LOCK, "PUT", 200, "application/json", body);
        assertThat(body).containsEntry("priceLockedUntil", lockedUntil.toString());
        assertThat(audited(clinic.id(), "SUBSCRIPTION_PRICE_LOCK_UPDATED", admin.accountId())).isOne();
        assertCode(api.put(TENANTS + "/" + clinic.id() + "/subscription/price-lock", admin.token(),
                Map.of("lockedUntil", lockedUntil.toString(), "version", 0)), HttpStatus.CONFLICT, "VERSION_CONFLICT");

        createPrice(planId, "MONTHLY", "79.00", Instant.now());

        Instant appliesFrom = jdbc.queryForObject("select applies_from from price_change_notice where tenant_id = ?",
                Timestamp.class, clinic.id()).toInstant();
        assertThat(LocalDate.ofInstant(appliesFrom, GUAYAQUIL)).isAfter(lockedUntil);
        for (int renewal = 0; renewal < 3; renewal++) {
            publisher.publishEvent(new PaymentReceived(clinic.id(), "pay-" + UUID.randomUUID(), BigDecimal.ONE, "USD",
                    Instant.now()));
        }
        assertThat(maps(getSubscription(clinic.id()).get("periods")))
                .allSatisfy(period -> assertThat(period).containsEntry("amount", "49.00"));

        Map<String, Object> current = getSubscription(clinic.id());
        Map<String, Object> unlocked = api.body(api.put(TENANTS + "/" + clinic.id() + "/subscription/price-lock",
                admin.token(), mapOf("lockedUntil", null, "version", version(current))));
        assertThat(unlocked).containsEntry("priceLockedUntil", null);
    }

    // ------------------------------------------------------------------ isolation and logs

    @Test
    void aTransitionOnOneClinicNeverTouchesAnother() {
        Onboarded clinicA = onboard(createPaidPlan());
        Onboarded clinicB = onboard(createPaidPlan());
        Map<String, Object> before = jdbc.queryForMap("select status, version, current_period_end from subscription where tenant_id = ?",
                clinicB.id());

        assertThat(transition(clinicA.id(), "SUSPENDED", null, 0)).hasStatus(HttpStatus.OK);
        publisher.publishEvent(new PaymentReceived(clinicA.id(), "pay-" + UUID.randomUUID(), BigDecimal.ONE, "USD",
                Instant.now()));

        assertThat(jdbc.queryForMap("select status, version, current_period_end from subscription where tenant_id = ?",
                clinicB.id())).isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                select count(*) from subscription_transition t join subscription s on s.id = t.subscription_id
                where t.tenant_id <> s.tenant_id""", Integer.class)).as("a transition never points to another clinic").isZero();
        assertThat(getSubscription(clinicA.id()).get("id")).isNotEqualTo(getSubscription(clinicB.id()).get("id"));
        Clinic tenantUser = api.clinic("Clinica Curiosa " + UUID.randomUUID().toString().substring(0, 8));
        assertThat(api.get(TENANTS + "/" + clinicA.id() + "/subscription", tenantUser.token())).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void noOwnerEmailClinicNameNorReasonDetailsReachTheLogs(CapturedOutput output) {
        Onboarded clinic = onboard(createPaidPlan());
        Map<String, Object> cancelled = api.body(transition(clinic.id(), "CANCELLED", null, 0));
        publisher.publishEvent(new PaymentReceived(clinic.id(), "pay-" + UUID.randomUUID(), BigDecimal.ONE, "USD",
                Instant.now()));
        transition(clinic.id(), "ACTIVE", "Motivo con detalle reservado", version(cancelled));

        assertThat(output.getAll()).doesNotContain(clinic.ownerEmail()).doesNotContain(clinic.legalName())
                .doesNotContain("Motivo con detalle reservado");
        assertThat(jdbc.queryForList("select distinct target_type from audit_event where tenant_id = ?", String.class,
                clinic.id())).containsOnly("TENANT", "SUBSCRIPTION");
    }

    // ------------------------------------------------------------------ helpers

    private record Onboarded(UUID id, String ownerEmail, String legalName) {}

    private Onboarded onboard(String planId) {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("fullName", "Ana Prueba");
        owner.put("email", "ana." + marker + "@example.test");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("legalName", "Clínica Ciclo " + marker + " S.A.");
        request.put("ruc", String.format("%013d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 10_000_000_000_000L)));
        request.put("clinicType", "MEDICAL");
        request.put("countryCode", "EC");
        request.put("city", "Quito");
        request.put("address", "Avenida Ejemplo 123");
        request.put("phone", "+593000000000");
        request.put("contactEmail", "contacto." + marker + "@example.test");
        request.put("planId", planId);
        request.put("billingPeriod", "MONTHLY");
        request.put("owner", owner);
        MvcTestResult created = api.post(TENANTS, admin.token(), request);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return new Onboarded(UUID.fromString(api.body(created).get("id").toString()), owner.get("email").toString(),
                request.get("legalName").toString());
    }

    private String createPaidPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("code", "PLAN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        plan.put("name", "Estándar");
        plan.put("trial", false);
        plan.put("modules", List.of("AGENDA", "PATIENTS", "USERS"));
        plan.put("limits", Map.of("MAX_USERS", 15));
        plan.put("prices", List.of(
                Map.of("billingPeriod", "MONTHLY", "currency", "USD", "amount", "49.00"),
                Map.of("billingPeriod", "ANNUAL", "currency", "USD", "amount", "490.00")));
        MvcTestResult created = api.post("/api/v1/platform/plans", admin.token(), plan);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return api.body(created).get("id").toString();
    }

    private void createPrice(String planId, String billingPeriod, String amount, Instant effectiveFrom) {
        assertThat(api.post("/api/v1/platform/plans/" + planId + "/prices", admin.token(), Map.of(
                "billingPeriod", billingPeriod, "currency", "USD", "amount", amount,
                "effectiveFrom", effectiveFrom.toString()))).hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult transition(UUID tenantId, String target, String reason, long version) {
        return api.post(TENANTS + "/" + tenantId + "/subscription/transitions", admin.token(),
                mapOf("target", target, "reason", reason, "version", version));
    }

    private Map<String, Object> getSubscription(UUID tenantId) {
        MvcTestResult result = getRaw(tenantId);
        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = api.body(result);
        contract.assertResponse(SUBSCRIPTION, "GET", 200, "application/json", body);
        return body;
    }

    private MvcTestResult getRaw(UUID tenantId) {
        return api.get(TENANTS + "/" + tenantId + "/subscription", admin.token());
    }

    private int transitions(UUID tenantId, String cause) {
        return jdbc.queryForObject("select count(*) from subscription_transition where tenant_id = ? and cause = ?",
                Integer.class, tenantId, cause);
    }

    private int audited(UUID tenantId, String action, UUID actorId) {
        return jdbc.queryForObject("""
                select count(*) from audit_event
                where tenant_id = ? and action = ? and target_type = 'SUBSCRIPTION'
                  and actor_account_id is not distinct from ?""",
                Integer.class, tenantId, action, actorId);
    }

    private int setting(String key) {
        return jdbc.queryForObject("select value from platform_setting where key = ?", Integer.class, key);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(GUAYAQUIL).toInstant();
    }

    private static long version(Map<String, Object> subscription) {
        return ((Number) subscription.get("version")).longValue();
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson()
                .hasPathSatisfying("$.code", value -> assertThat(value).asString().isEqualTo(code));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
