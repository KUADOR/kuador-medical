package com.kuador.medical.platform.tenant;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.OwnerOnboarding;
import com.kuador.medical.platform.idempotency.PlatformIdempotentCreations;
import com.kuador.medical.platform.plan.PlanCatalog;
import com.kuador.medical.platform.plan.PlanCatalog.OfferedPlan;
import com.kuador.medical.platform.subscription.Subscription;
import com.kuador.medical.platform.subscription.SubscriptionOpening;
import com.kuador.medical.platform.subscription.SubscriptionStatus;
import com.kuador.medical.platform.tenant.TenantRequests.CreateTenant;
import com.kuador.medical.platform.tenant.TenantResponses.OwnerResponse;
import com.kuador.medical.platform.tenant.TenantResponses.SubscriptionSummary;
import com.kuador.medical.platform.tenant.TenantResponses.TenantListItem;
import com.kuador.medical.platform.tenant.TenantResponses.TenantPage;
import com.kuador.medical.platform.tenant.TenantResponses.TenantResponse;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.tenancy.TenantCreated;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Clinic onboarding and administration by the SUPER_ADMIN (MEDK-66, FR-007, FR-011, plan D4 and
 * D8). The clinic, its seeded roles (on {@link TenantCreated}) and its subscription are created in
 * one transaction with the new clinic bound in the tenant context (ADR-007), so a failure leaves
 * nothing behind. Every change is audited as an event of the clinic; personal and tax data never
 * reach the logs nor the audit trail, which keeps identifiers only.
 */
@Service
class TenantOnboardingService {

    static final String CREATE_OPERATION = "platform.tenants.create";
    static final int MAX_PAGE_SIZE = 100;

    private static final String RUC_UNIQUE = "tenant_ruc_unique_idx";
    private static final String TENANT_PRIMARY_KEY = "tenant_pkey";

    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final TenantRepository tenants;
    private final RucValidator rucValidator;
    private final PlanCatalog plans;
    private final SubscriptionOpening subscriptions;
    private final PlatformIdempotentCreations idempotency;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final OwnerOnboarding owners;
    private final Clock clock = Clock.systemUTC();

    TenantOnboardingService(
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            TenantRepository tenants,
            RucValidator rucValidator,
            PlanCatalog plans,
            SubscriptionOpening subscriptions,
            PlatformIdempotentCreations idempotency,
            AuditRecorder audit,
            ApplicationEventPublisher events,
            JdbcTemplate jdbc,
            OwnerOnboarding owners) {
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.tenants = tenants;
        this.rucValidator = rucValidator;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.idempotency = idempotency;
        this.audit = audit;
        this.events = events;
        this.jdbc = jdbc;
        this.owners = owners;
    }

    /** {@code POST /platform/tenants}. */
    TenantResponse create(CreateTenant request, @Nullable String idempotencyKey, AuthenticatedAccount actor) {
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        UUID created = TenantContext.callAs(id, () -> Objects.requireNonNull(transaction.execute(status -> idempotency
                .create(actor.accountId(), CREATE_OPERATION, idempotencyKey, request, () -> doCreate(id, request, actor))
                .resourceId())));
        return get(created);
    }

    private UUID doCreate(UUID id, CreateTenant request, AuthenticatedAccount actor) {
        OfferedPlan plan = plans.find(request.planId()).orElseThrow(ApiException::notFound);
        if (plan.archived()) {
            throw ApiException.conflict(ErrorCode.PLAN_ARCHIVED, "The plan is archived.");
        }
        if (!plan.trial() && plan.priceFor(request.billingPeriod()).isEmpty()) {
            throw ApiException.validation(List.of(new FieldViolation("billingPeriod", "PRICE_NOT_AVAILABLE")));
        }
        rucValidator.ensureAvailable(request.ruc(), null);
        if (tenants.existsById(id)) {
            throw idTaken();
        }
        Instant now = clock.instant();
        entityManager.persist(Tenant.onboard(id, request.data(), request.owner().fullName().strip(),
                request.owner().email().strip(), actor.accountId(), now));
        flush();
        // Identity seeds the roles and invites the OWNER in this transaction (MEDK-67).
        events.publishEvent(new TenantCreated(id, new TenantCreated.InitialOwner(
                request.owner().fullName().strip(), request.owner().email().strip())));
        subscriptions.open(plan, request.billingPeriod(), now);
        flush();
        audit.record(AuditAction.TENANT_ONBOARDED, id, actor.accountId(), AuditTarget.tenant(id));
        return id;
    }

    /** {@code GET /platform/tenants/{tenantId}}: purged and non-onboarded clinics are not found. */
    TenantResponse get(UUID id) {
        return TenantContext.callAs(id, () -> Objects.requireNonNull(readOnly.execute(status -> view(find(id)))));
    }

    /** {@code PATCH /platform/tenants/{tenantId}}. */
    TenantResponse update(UUID id, TenantUpdate update, AuthenticatedAccount actor) {
        return TenantContext.callAs(id, () -> Objects.requireNonNull(transaction.execute(status -> {
            Tenant tenant = find(id);
            if (tenant.getVersion() != update.version()) {
                throw versionConflict();
            }
            String newRuc = update.newRuc();
            if (newRuc != null) {
                rucValidator.ensureAvailable(newRuc, id);
            }
            tenant.update(update.applyTo(tenant.data()), clock.instant());
            flush();
            audit.record(AuditAction.TENANT_UPDATED, id, actor.accountId(), AuditTarget.tenant(id));
            return view(tenant);
        })));
    }

    /**
     * {@code GET /platform/tenants}: onboarded, non-purged clinics, newest first. A platform read
     * across clinics, reachable only with {@code platform.tenants.manage}.
     */
    TenantPage list(@Nullable String q, @Nullable SubscriptionStatus status, int page, int size) {
        List<FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new FieldViolation("page", "INVALID_FORMAT"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add(new FieldViolation("size", "INVALID_FORMAT"));
        }
        if (q != null && q.length() > 120) {
            violations.add(new FieldViolation("q", "INVALID_LENGTH"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        StringBuilder where = new StringBuilder(" where t.purged_at is null and t.ruc is not null");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" and s.status = ?");
            args.add(status.name());
        }
        String search = q == null || q.isBlank() ? null : q.strip().toLowerCase(Locale.ROOT);
        if (search != null) {
            String like = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" and (lower(t.legal_name) like ? or lower(coalesce(t.trade_name, '')) like ? or t.ruc like ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String from = " from tenant t join subscription s on s.tenant_id = t.id join plan p on p.id = s.plan_id";
        return Objects.requireNonNull(readOnly.execute(tx -> {
            Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(size);
            pageArgs.add((long) page * size);
            List<TenantListItem> items = jdbc.query("""
                    select t.id, t.legal_name, t.trade_name, t.ruc, t.clinic_type, p.name as plan_name,
                           s.status, t.created_at""" + from + where + " order by t.created_at desc, t.id limit ? offset ?",
                    (rs, row) -> new TenantListItem(
                            rs.getObject("id", UUID.class),
                            rs.getString("legal_name"),
                            rs.getString("trade_name"),
                            rs.getString("ruc"),
                            ClinicType.valueOf(rs.getString("clinic_type")),
                            rs.getString("plan_name"),
                            SubscriptionStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("created_at").toInstant()),
                    pageArgs.toArray());
            return new TenantPage(page, size, total == null ? 0 : total, items);
        }));
    }

    private Tenant find(UUID id) {
        return tenants.findOnboarded(id).orElseThrow(ApiException::notFound);
    }

    /** Needs the clinic bound in the tenant context, to read its subscription. */
    private TenantResponse view(Tenant tenant) {
        Subscription subscription = subscriptions.current().orElseThrow(ApiException::notFound);
        String planName = plans.find(subscription.getPlanId()).map(OfferedPlan::name).orElseThrow(ApiException::notFound);
        ClinicData data = tenant.data();
        String ownerEmail = Objects.requireNonNull(tenant.getOwnerEmail());
        OwnerOnboarding.OwnerStatus owner = owners.status();
        return new TenantResponse(
                tenant.getId(),
                data.legalName(),
                data.tradeName(),
                data.ruc(),
                data.clinicType(),
                data.countryCode(),
                data.city(),
                data.address(),
                data.phone(),
                data.contactEmail(),
                data.mspEstablishmentNumber(),
                new OwnerResponse(Objects.requireNonNull(tenant.getOwnerFullName()),
                        TenantResponses.maskEmail(ownerEmail), owner.twoFactorEnabled(),
                        TenantResponses.InvitationResponse.of(owner.invitation())),
                new SubscriptionSummary(
                        subscription.getStatus(),
                        subscription.getPlanId(),
                        planName,
                        subscription.getBillingPeriod(),
                        subscription.getCurrentPeriodEnd(),
                        subscription.getTrialEndsAt()),
                tenant.getCreatedAt(),
                tenant.getVersion());
    }

    /** Races the pre-checks cannot see are decided by the database. */
    private void flush() {
        try {
            entityManager.flush();
        } catch (OptimisticLockException e) {
            throw versionConflict();
        } catch (PersistenceException e) {
            String constraint = violatedConstraint(e);
            if (RUC_UNIQUE.equals(constraint)) {
                throw RucValidator.alreadyRegistered();
            }
            if (TENANT_PRIMARY_KEY.equals(constraint)) {
                throw idTaken();
            }
            throw e;
        }
    }

    private static @Nullable String violatedConstraint(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    private static ApiException versionConflict() {
        return ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The clinic changed since it was read.");
    }

    private static ApiException idTaken() {
        return ApiException.conflict(ErrorCode.RESOURCE_ID_TAKEN, "The id is already used.");
    }
}
