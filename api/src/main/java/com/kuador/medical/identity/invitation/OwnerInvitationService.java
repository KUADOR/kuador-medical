package com.kuador.medical.identity.invitation;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.OwnerInvitationPolicy;
import com.kuador.medical.identity.OwnerOnboarding;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.identity.account.ClinicAccount;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import com.kuador.medical.identity.account.ClinicAccountStore;
import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.account.LinkProperties;
import com.kuador.medical.identity.account.PasswordPolicy;
import com.kuador.medical.identity.role.Role;
import com.kuador.medical.identity.role.RoleAssignment;
import com.kuador.medical.identity.role.RoleRepository;
import com.kuador.medical.identity.role.RoleSeedingOrder;
import com.kuador.medical.identity.twofactor.TwoFactorService;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.notifications.PlatformEmails;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.tenancy.TenantCreated;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invitation of the initial OWNER of a clinic onboarded by the SUPER_ADMIN (MEDK-67, FR-008,
 * ADR-014). When the platform publishes {@link TenantCreated} with an initial owner, an
 * {@code INVITED} account with the clinic-wide OWNER role and a one-time invitation are created in
 * the same transaction; the email leaves after commit. The token (32 random bytes, Base64URL)
 * travels only in the fragment of the link and in request bodies, and only its SHA-256 is stored.
 * A new invitation revokes the pending one; the expiry comes from {@code OWNER_INVITATION_TTL_HOURS}.
 * Accepting sets the password, records the terms and data policy versions and consumes the token;
 * the session then still requires the TOTP second factor (ADR-019).
 */
@Service
@EnableConfigurationProperties(LegalProperties.class)
public class OwnerInvitationService implements OwnerOnboarding {

    static final String LINK_PATH = "/owner-invitation";
    private static final String LOGIN_PATH = "/login";
    private static final Logger log = LoggerFactory.getLogger(OwnerInvitationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What the invited OWNER sees before accepting. */
    public record Preview(String clinicName, String ownerName, String maskedEmail, Instant expiresAt,
            String termsVersion, String dataPolicyVersion) {}

    /** An accepted invitation: the OWNER account is active in its clinic. */
    public record Accepted(UUID tenantId, UUID accountId) {}

    /** The acceptance request, already parsed. */
    public record Acceptance(String token, String password, boolean acceptTerms, String termsVersion,
            boolean acceptDataPolicy, String dataPolicyVersion) {

        @Override
        public String toString() {
            return "Acceptance[redacted]";
        }
    }

    private record StoredInvitation(UUID id, UUID tenantId, UUID accountId, Instant createdAt, Instant expiresAt,
            @Nullable Instant usedAt, @Nullable Instant revokedAt) {

        InvitationStatus status(Instant now) {
            if (usedAt != null) {
                return InvitationStatus.ACCEPTED;
            }
            if (revokedAt != null) {
                return InvitationStatus.REVOKED;
            }
            return expiresAt.isAfter(now) ? InvitationStatus.PENDING : InvitationStatus.EXPIRED;
        }
    }

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final ClinicAccountRepository accounts;
    private final ClinicAccountStore store;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TenantDirectory tenants;
    private final NotificationSender notifications;
    private final AuditRecorder audit;
    private final LinkProperties links;
    private final OwnerInvitationPolicy policy;
    private final LegalProperties legal;
    private final TwoFactorService twoFactors;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    OwnerInvitationService(
            JdbcTemplate jdbc,
            EntityManager entityManager,
            ClinicAccountRepository accounts,
            ClinicAccountStore store,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            TenantDirectory tenants,
            NotificationSender notifications,
            AuditRecorder audit,
            LinkProperties links,
            OwnerInvitationPolicy policy,
            LegalProperties legal,
            TwoFactorService twoFactors,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.store = store;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tenants = tenants;
        this.notifications = notifications;
        this.audit = audit;
        this.links = links;
        this.policy = policy;
        this.legal = legal;
        this.twoFactors = twoFactors;
        this.transaction = new TransactionTemplate(transactions);
    }

    /**
     * Creates the invited OWNER of a clinic onboarded by the SUPER_ADMIN, after its roles are
     * seeded (same transaction, same clinic bound).
     */
    @EventListener
    @Order(RoleSeedingOrder.AFTER)
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TenantCreated event) {
        TenantCreated.InitialOwner owner = event.initialOwner();
        if (owner == null) {
            return;
        }
        if (!TenantContext.current().equals(Optional.of(event.tenantId()))) {
            throw new IllegalStateException("The new tenant must be the current tenant");
        }
        Role ownerRole = roles.findAllInCurrentTenant().stream()
                .filter(Role::isOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The roles of the tenant must be seeded first"));
        ClinicAccount account = ClinicAccount.invitedOwner(owner.email(), owner.fullName());
        store.persistNew(account);
        entityManager.persist(new RoleAssignment(account.getId(), null, ownerRole.getId()));
        entityManager.flush();
        audit.record(AuditAction.ACCOUNT_PROVISIONED, event.tenantId(), null, AuditTarget.account(account.getId()));
        issue(event.tenantId(), account, null);
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerStatus status() {
        Optional<StoredInvitation> latest = latest(currentTenant());
        if (latest.isEmpty()) {
            return new OwnerStatus(false, null);
        }
        StoredInvitation invitation = latest.get();
        ClinicAccount account = accounts.findInCurrentTenant(invitation.accountId()).orElseThrow(ApiException::notFound);
        return new OwnerStatus(twoFactors.isEnabled(account.getId()), new Invitation(
                invitation.id(), invitation.status(clock.instant()), EmailAddresses.mask(account.getEmail()),
                invitation.createdAt(), invitation.expiresAt()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID resendInvitation(@Nullable String newEmail, UUID actor) {
        UUID tenantId = currentTenant();
        StoredInvitation latest = latest(tenantId).orElseThrow(ApiException::notFound);
        ClinicAccount account = accounts.findInCurrentTenant(latest.accountId()).orElseThrow(ApiException::notFound);
        if (account.getStatus() != ClinicAccountStatus.INVITED) {
            throw ApiException.conflict(ErrorCode.ACCOUNT_ALREADY_ACTIVE, "The OWNER already accepted the invitation.");
        }
        if (newEmail != null && !EmailAddresses.normalize(newEmail).equals(account.getEmail())) {
            if (accounts.existsByEmailInCurrentTenant(EmailAddresses.normalize(newEmail))) {
                throw ClinicAccountStore.emailTaken();
            }
            account.correctInvitedEmail(newEmail, actor);
            entityManager.flush();
        }
        return issue(tenantId, account, actor);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resetTwoFactor(String reason, UUID actor) {
        UUID tenantId = currentTenant();
        StoredInvitation latest = latest(tenantId).orElseThrow(ApiException::notFound);
        ClinicAccount account = accounts.findInCurrentTenant(latest.accountId()).orElseThrow(ApiException::notFound);
        twoFactors.reset(account.getId(), actor, reason);
        String clinicName = tenants.find(tenantId).map(TenantDirectory.TenantInfo::name).orElseThrow();
        String loginLink = stripTrailingSlash(links.webBaseUrl()) + LOGIN_PATH;
        afterCommit(account.getId(), PlatformEmails.twoFactorReset(account.getEmail(), clinicName, loginLink));
    }

    /**
     * {@code POST /public/owner-invitations/preview}.
     *
     * @throws ApiException {@code INVITATION_INVALID_OR_EXPIRED}
     */
    public Preview preview(String token) {
        StoredInvitation invitation = valid(token);
        return TenantContext.callAs(invitation.tenantId(), () -> Objects.requireNonNull(transaction.execute(status -> {
            String clinicName = activeClinicName(invitation.tenantId());
            ClinicAccount account = invitedAccount(invitation.accountId());
            return new Preview(clinicName, account.getDisplayName(), EmailAddresses.mask(account.getEmail()),
                    invitation.expiresAt(), legal.termsVersion(), legal.dataPolicyVersion());
        })));
    }

    /**
     * {@code POST /public/owner-invitations/accept}: an invalid token answers 410 before anything
     * else; outdated versions ({@code VALIDATION}) and a weak password ({@code WEAK_PASSWORD})
     * leave the token valid.
     */
    public Accepted accept(Acceptance acceptance) {
        StoredInvitation invitation = valid(acceptance.token());
        List<FieldViolation> violations = new ArrayList<>();
        if (!acceptance.acceptTerms()) {
            violations.add(new FieldViolation("acceptTerms", "REQUIRED"));
        }
        if (!legal.termsVersion().equals(acceptance.termsVersion())) {
            violations.add(new FieldViolation("termsVersion", "OUTDATED_VERSION"));
        }
        if (!acceptance.acceptDataPolicy()) {
            violations.add(new FieldViolation("acceptDataPolicy", "REQUIRED"));
        }
        if (!legal.dataPolicyVersion().equals(acceptance.dataPolicyVersion())) {
            violations.add(new FieldViolation("dataPolicyVersion", "OUTDATED_VERSION"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        passwordPolicy.checkFirstPassword(acceptance.password());
        String passwordHash = passwordEncoder.encode(acceptance.password());
        return TenantContext.callAs(invitation.tenantId(), () -> Objects.requireNonNull(transaction.execute(status -> {
            activeClinicName(invitation.tenantId());
            ClinicAccount account = invitedAccount(invitation.accountId());
            Instant now = clock.instant();
            int consumed = jdbc.update("""
                    update identity_owner_invitation set used_at = ?
                    where id = ? and tenant_id = ? and used_at is null and revoked_at is null and expires_at > ?""",
                    at(now), invitation.id(), invitation.tenantId(), at(now));
            if (consumed != 1) {
                throw gone();
            }
            account.acceptInvitation(passwordHash);
            accounts.saveAndFlush(account);
            jdbc.update("""
                    insert into identity_terms_acceptance (id, tenant_id, user_id, terms_version, data_policy_version, accepted_at)
                    values (?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), invitation.tenantId(), account.getId(), legal.termsVersion(),
                    legal.dataPolicyVersion(), at(now));
            audit.record(AuditAction.OWNER_INVITATION_ACCEPTED, invitation.tenantId(), account.getId(),
                    AuditTarget.account(account.getId()));
            audit.record(AuditAction.TERMS_ACCEPTED, invitation.tenantId(), account.getId(),
                    AuditTarget.account(account.getId()));
            return new Accepted(invitation.tenantId(), account.getId());
        })));
    }

    /** Revokes the pending invitation of the account, stores a new one and emails it after commit. */
    private UUID issue(UUID tenantId, ClinicAccount account, @Nullable UUID actor) {
        Instant now = clock.instant();
        jdbc.update("""
                update identity_owner_invitation set revoked_at = ?
                where tenant_id = ? and account_id = ? and used_at is null and revoked_at is null""",
                at(now), tenantId, account.getId());
        String token = newToken();
        Instant expiresAt = now.plus(policy.ownerInvitationTtl());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into identity_owner_invitation (id, tenant_id, account_id, token_hash, expires_at, created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId, account.getId(), hash(token), at(expiresAt), at(now), actor);
        String clinicName = tenants.find(tenantId).map(TenantDirectory.TenantInfo::name).orElseThrow();
        EmailMessage email = PlatformEmails.ownerInvitation(account.getEmail(), account.getDisplayName(), clinicName,
                links.withToken(LINK_PATH, token), expiresAt);
        audit.record(AuditAction.OWNER_INVITATION_SENT, tenantId, actor, AuditTarget.account(account.getId()));
        afterCommit(account.getId(), email);
        return id;
    }

    /** The pending invitation of a token, looked up by its hash before any clinic is bound. */
    private StoredInvitation valid(String token) {
        if (token == null || token.isBlank()) {
            throw gone();
        }
        return jdbc.query("""
                        select id, tenant_id, account_id, created_at, expires_at, used_at, revoked_at
                        from identity_owner_invitation where token_hash = ?""",
                        (rs, row) -> stored(rs), hash(token)).stream()
                .findFirst()
                .filter(invitation -> invitation.status(clock.instant()) == InvitationStatus.PENDING)
                .orElseThrow(OwnerInvitationService::gone);
    }

    private Optional<StoredInvitation> latest(UUID tenantId) {
        return jdbc.query("""
                        select id, tenant_id, account_id, created_at, expires_at, used_at, revoked_at
                        from identity_owner_invitation where tenant_id = ? order by created_at desc, id limit 1""",
                        (rs, row) -> stored(rs), tenantId).stream().findFirst();
    }

    private static StoredInvitation stored(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredInvitation(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                instant(rs.getObject("used_at", OffsetDateTime.class)),
                instant(rs.getObject("revoked_at", OffsetDateTime.class)));
    }

    private String activeClinicName(UUID tenantId) {
        return tenants.find(tenantId)
                .filter(TenantDirectory.TenantInfo::active)
                .map(TenantDirectory.TenantInfo::name)
                .orElseThrow(OwnerInvitationService::gone);
    }

    private ClinicAccount invitedAccount(UUID accountId) {
        return accounts.findInCurrentTenant(accountId)
                .filter(account -> account.getStatus() == ClinicAccountStatus.INVITED)
                .orElseThrow(OwnerInvitationService::gone);
    }

    private void afterCommit(UUID accountId, EmailMessage email) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(accountId, email);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(accountId, email);
            }
        });
    }

    private void deliver(UUID accountId, EmailMessage email) {
        try {
            notifications.sendEmail(email);
        } catch (RuntimeException e) {
            // No email, token nor name in the log (Constitution V): the account id is enough to resend.
            log.warn("OWNER email of account {} could not be sent: {}", accountId, e.getClass().getSimpleName());
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static UUID currentTenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static ApiException gone() {
        return new ApiException(HttpStatus.GONE, ErrorCode.INVITATION_INVALID_OR_EXPIRED,
                "The invitation is expired, used, revoked or unknown.");
    }
}
