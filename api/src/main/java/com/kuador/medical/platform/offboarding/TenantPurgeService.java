package com.kuador.medical.platform.offboarding;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.platform.api.TenantDataPurger;
import com.kuador.medical.platform.api.TenantPurgeCancelled;
import com.kuador.medical.platform.api.TenantPurgeRequested;
import com.kuador.medical.platform.api.TenantPurged;
import com.kuador.medical.platform.idempotency.PlatformIdempotentCreations;
import com.kuador.medical.platform.offboarding.OffboardingRequests.ConfirmPurgeRequest;
import com.kuador.medical.platform.offboarding.OffboardingRequests.CreatePurgeRequest;
import com.kuador.medical.platform.offboarding.OffboardingResponses.PurgeRequestResponse;
import com.kuador.medical.platform.subscription.Subscription;
import com.kuador.medical.platform.subscription.SubscriptionLifecycle;
import com.kuador.medical.platform.subscription.SubscriptionStatus;
import com.kuador.medical.platform.subscription.SubscriptionStatusChanged;
import com.kuador.medical.platform.tenant.Tenant;
import com.kuador.medical.platform.tenant.TenantRepository;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Purge of a clinic (ADR-018, plan D6 and D12): a SUPER_ADMIN requests it and then confirms it by
 * typing the RUC; both steps check that the clinic is {@code CANCELLED}, not purged and past its
 * retention. The confirmation locks the subscription (a concurrent reactivation fails), runs every
 * {@link TenantDataPurger} but the clinical record ones, marks the clinic purged and publishes
 * {@link TenantPurged}, in one transaction. Every step is audited; the RUC and the reason are never
 * logged.
 */
@Service
class TenantPurgeService {

    static final String REQUEST_OPERATION = "requestTenantPurge";

    private static final Logger log = LoggerFactory.getLogger(TenantPurgeService.class);

    private final TenantPurgeRequestRepository requests;
    private final TenantRepository tenants;
    private final SubscriptionLifecycle lifecycle;
    private final List<TenantDataPurger> purgers;
    private final PlatformIdempotentCreations idempotency;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    TenantPurgeService(
            TenantPurgeRequestRepository requests,
            TenantRepository tenants,
            SubscriptionLifecycle lifecycle,
            List<TenantDataPurger> purgers,
            PlatformIdempotentCreations idempotency,
            AuditRecorder audit,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.requests = requests;
        this.tenants = tenants;
        this.lifecycle = lifecycle;
        List<TenantDataPurger> ordered = new ArrayList<>(purgers);
        AnnotationAwareOrderComparator.sort(ordered);
        this.purgers = List.copyOf(ordered);
        this.idempotency = idempotency;
        this.audit = audit;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** {@code POST /platform/tenants/{tenantId}/purge-requests}: first step, nothing is deleted. */
    PurgeRequestResponse request(UUID tenantId, CreatePurgeRequest request, @Nullable String idempotencyKey,
            AuthenticatedAccount actor) {
        return inClinic(tenantId, () -> {
            UUID id = idempotency.create(actor.accountId(), REQUEST_OPERATION + ":" + tenantId, idempotencyKey, request,
                    () -> create(tenantId, request, actor.accountId())).resourceId();
            return PurgeRequestResponse.of(requests.findById(id).orElseThrow(ApiException::notFound));
        });
    }

    /** {@code POST .../purge-requests/{requestId}/confirm}: second step, the purge runs. */
    PurgeRequestResponse confirm(UUID tenantId, UUID requestId, ConfirmPurgeRequest confirmation,
            AuthenticatedAccount actor) {
        return inClinic(tenantId, () -> {
            // Lock first: a reactivation racing this confirmation either committed (and cancelled the
            // request, seen below) or waits and then fails with a version conflict.
            Subscription subscription = lifecycle.lockForPurge().orElseThrow(ApiException::notFound);
            TenantPurgeRequest request = requests.findById(requestId).orElseThrow(ApiException::notFound);
            if (request.getStatus() != TenantPurgeRequest.Status.REQUESTED) {
                throw PurgeEligibility.notAllowed("The purge request is " + request.getStatus() + ".");
            }
            Tenant tenant = tenants.findById(tenantId).orElseThrow(ApiException::notFound);
            if (!sameRuc(tenant.getRuc(), confirmation.rucConfirmation())) {
                throw ApiException.validation(List.of(new FieldViolation("rucConfirmation", "MISMATCH")));
            }
            Instant now = clock.instant();
            PurgeEligibility.check(subscription.getStatus(), subscription.getRetentionEndsAt(),
                    tenant.getPurgedAt() != null, now);
            request.confirm(actor.accountId(), now);
            requests.flush();
            audit.record(AuditAction.TENANT_PURGE_CONFIRMED, tenantId, actor.accountId(),
                    AuditTarget.purgeRequest(request.getId()));
            purge(tenantId);
            audit.record(AuditAction.TENANT_PURGED, tenantId, actor.accountId(), AuditTarget.tenant(tenantId));
            events.publishEvent(new TenantPurged(tenantId, request.getId(), now));
            return PurgeRequestResponse.of(request);
        });
    }

    /**
     * A reactivation from CANCELLED (plan D12) cancels the pending purge request, in the
     * transaction of the reactivation (the event is published inside it, with the clinic bound).
     */
    @EventListener
    void on(SubscriptionStatusChanged change) {
        if (change.from() != SubscriptionStatus.CANCELLED || change.to() == SubscriptionStatus.CANCELLED) {
            return;
        }
        for (TenantPurgeRequest pending : requests.findByStatus(TenantPurgeRequest.Status.REQUESTED)) {
            pending.cancel(change.at());
            requests.flush();
            audit.record(AuditAction.TENANT_PURGE_CANCELLED, change.tenantId(), null,
                    AuditTarget.purgeRequest(pending.getId()));
            events.publishEvent(new TenantPurgeCancelled(change.tenantId(), pending.getId(), change.at()));
        }
    }

    private UUID create(UUID tenantId, CreatePurgeRequest body, UUID actorId) {
        Subscription subscription = lifecycle.lockForPurge().orElseThrow(ApiException::notFound);
        Instant now = clock.instant();
        PurgeEligibility.check(subscription.getStatus(), subscription.getRetentionEndsAt(), false, now);
        if (!requests.findByStatus(TenantPurgeRequest.Status.REQUESTED).isEmpty()) {
            throw PurgeEligibility.notAllowed("Another purge request is pending.");
        }
        if (body.id() != null && requests.existsById(body.id())) {
            throw ApiException.conflict(ErrorCode.RESOURCE_ID_TAKEN, "A purge request with this id already exists.");
        }
        TenantPurgeRequest request = requests.saveAndFlush(new TenantPurgeRequest(
                body.id() != null ? body.id() : UUID.randomUUID(), body.reason().strip(), actorId, now));
        audit.record(AuditAction.TENANT_PURGE_REQUESTED, tenantId, actorId, AuditTarget.purgeRequest(request.getId()));
        events.publishEvent(new TenantPurgeRequested(tenantId, request.getId(), now));
        return request.getId();
    }

    /** Runs every purger but the clinical record ones, disabled until the legal validation (ADR-018). */
    private void purge(UUID tenantId) {
        for (TenantDataPurger purger : purgers) {
            if (purger.purgesClinicalRecords()) {
                log.warn("Purger {} skipped for tenant {}: clinical record purge is disabled (ADR-018)",
                        purger.name(), tenantId);
                continue;
            }
            purger.purge();
        }
        log.info("Tenant {} purged by {} purgers", tenantId, purgers.size());
    }

    /** Only onboarded clinics that were not purged; the clinic is bound for the whole transaction. */
    private <T> T inClinic(UUID tenantId, Supplier<T> work) {
        if (tenants.findOnboarded(tenantId).isEmpty()) {
            throw ApiException.notFound();
        }
        return TenantContext.callAs(tenantId, () -> Objects.requireNonNull(transaction.execute(status -> work.get())));
    }

    private static boolean sameRuc(@Nullable String stored, String typed) {
        return stored != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.US_ASCII), typed.getBytes(StandardCharsets.US_ASCII));
    }
}
