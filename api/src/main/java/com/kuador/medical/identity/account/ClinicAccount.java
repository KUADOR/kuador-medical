package com.kuador.medical.identity.account;

import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Account of a person in one clinic (ADR-012): staff, patient or guardian. The email is unique
 * only inside the clinic. Tenant-scoped (ADR-007). An invited account has no password until it
 * accepts its invitation (ADR-014).
 */
@Entity
@Table(name = "identity_user_account")
public class ClinicAccount implements Persistable<UUID> {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "email_normalized", nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash")
    private @Nullable String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClinicAccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccountKind kind;

    @Column(name = "first_names", nullable = false)
    private String firstNames;

    @Column(name = "last_names", nullable = false)
    private String lastNames;

    @Column(name = "job_title")
    private @Nullable String jobTitle;

    @Column(name = "professional_registration")
    private @Nullable String professionalRegistration;

    @Column(name = "patient_ref", updatable = false)
    private @Nullable UUID patientRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private @Nullable UUID updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Transient
    private boolean persisted;

    protected ClinicAccount() {}

    private ClinicAccount(
            UUID id,
            AccountKind kind,
            String email,
            String firstNames,
            String lastNames,
            ClinicAccountStatus status,
            @Nullable UUID createdBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = kind;
        this.email = EmailAddresses.normalize(email);
        this.firstNames = firstNames.strip();
        this.lastNames = lastNames.strip();
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    /** A staff user waiting for its invitation to be accepted. */
    public static ClinicAccount invitedStaff(
            UUID id,
            String email,
            String firstNames,
            String lastNames,
            String jobTitle,
            @Nullable String professionalRegistration,
            UUID createdBy) {
        ClinicAccount account = new ClinicAccount(
                id, AccountKind.STAFF, email, firstNames, lastNames, ClinicAccountStatus.INVITED, createdBy);
        account.jobTitle = jobTitle.strip();
        account.professionalRegistration = blankToNull(professionalRegistration);
        return account;
    }

    /** An invited patient or guardian account (AccountProvisioning). */
    public static ClinicAccount invited(
            AccountKind kind, String email, String firstNames, String lastNames, @Nullable UUID patientRef) {
        if (kind == AccountKind.STAFF) {
            throw new IllegalArgumentException("Staff users are registered through /staff-users");
        }
        ClinicAccount account = new ClinicAccount(
                UUID.randomUUID(), kind, email, firstNames, lastNames, ClinicAccountStatus.INVITED, null);
        account.patientRef = patientRef;
        return account;
    }

    /** A self-registered patient that chose its password and must verify its email first. */
    public static ClinicAccount pendingEmailVerification(
            String email, String passwordHash, String firstNames, String lastNames, UUID patientRef) {
        ClinicAccount account = new ClinicAccount(
                UUID.randomUUID(), AccountKind.PATIENT, email, firstNames, lastNames,
                ClinicAccountStatus.PENDING_EMAIL_VERIFICATION, null);
        account.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        account.patientRef = patientRef;
        return account;
    }

    /** Activates a self-registered account once its email is verified. */
    public void verifyEmail() {
        if (status != ClinicAccountStatus.PENDING_EMAIL_VERIFICATION) {
            throw new IllegalStateException("Only an account pending verification verifies its email");
        }
        this.status = ClinicAccountStatus.ACTIVE;
        touch(id);
    }

    /** The owner of a clinic being signed up, with the password it chose. */
    public static ClinicAccount activeOwner(String email, String passwordHash, String displayName) {
        String name = displayName.strip();
        int space = name.indexOf(' ');
        String first = space < 0 ? name : name.substring(0, space);
        String last = space < 0 ? "" : name.substring(space + 1).strip();
        ClinicAccount account = new ClinicAccount(
                UUID.randomUUID(), AccountKind.STAFF, email, first, last, ClinicAccountStatus.ACTIVE, null);
        account.passwordHash = passwordHash;
        account.jobTitle = "";
        return account;
    }

    /** Sets the first password of an invited account and activates it. */
    public void acceptInvitation(String newPasswordHash) {
        if (status != ClinicAccountStatus.INVITED) {
            throw new IllegalStateException("Only an invited account accepts an invitation");
        }
        this.passwordHash = newPasswordHash;
        this.status = ClinicAccountStatus.ACTIVE;
        touch(id);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        touch(id);
    }

    /**
     * Partial update of the personal data; a {@code null} argument keeps the current value, except
     * {@code professionalRegistration} when {@code changeRegistration} is true.
     */
    public void updatePersonalData(
            @Nullable String newFirstNames,
            @Nullable String newLastNames,
            @Nullable String newJobTitle,
            boolean changeRegistration,
            @Nullable String newRegistration,
            long expectedVersion,
            UUID actor) {
        ensureVersion(expectedVersion);
        if (newFirstNames != null) {
            this.firstNames = newFirstNames.strip();
        }
        if (newLastNames != null) {
            this.lastNames = newLastNames.strip();
        }
        if (newJobTitle != null) {
            this.jobTitle = newJobTitle.strip();
        }
        if (changeRegistration) {
            this.professionalRegistration = blankToNull(newRegistration);
        }
        touch(actor);
    }

    public void disable(UUID actor) {
        this.status = ClinicAccountStatus.DISABLED;
        touch(actor);
    }

    /** Back to {@code ACTIVE}, or to {@code INVITED} if it never chose a password. */
    public void enable(UUID actor) {
        this.status = passwordHash == null ? ClinicAccountStatus.INVITED : ClinicAccountStatus.ACTIVE;
        touch(actor);
    }

    public void ensureVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The staff user was modified by someone else.");
        }
    }

    private void touch(@Nullable UUID actor) {
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public @Nullable String getPasswordHash() {
        return passwordHash;
    }

    public ClinicAccountStatus getStatus() {
        return status;
    }

    public AccountKind getKind() {
        return kind;
    }

    public String getFirstNames() {
        return firstNames;
    }

    public String getLastNames() {
        return lastNames;
    }

    public String getDisplayName() {
        return (firstNames + " " + lastNames).strip();
    }

    public @Nullable String getJobTitle() {
        return jobTitle;
    }

    public @Nullable String getProfessionalRegistration() {
        return professionalRegistration;
    }

    public @Nullable UUID getPatientRef() {
        return patientRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == ClinicAccountStatus.ACTIVE;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        this.persisted = true;
    }
}
