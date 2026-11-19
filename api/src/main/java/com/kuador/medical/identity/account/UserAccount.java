package com.kuador.medical.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Platform (SUPER_ADMIN) account: credentials and an email unique on the platform, without
 * clinic. Clinic accounts are {@link ClinicAccount}, one per clinic (ADR-012).
 */
@Entity
@Table(name = "user_account")
public class UserAccount implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "platform_admin", nullable = false, updatable = false)
    private boolean platformAdmin;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean persisted;

    protected UserAccount() {}

    private UserAccount(
            String email, String passwordHash, String displayName, boolean platformAdmin, boolean mustChangePassword) {
        this.id = UUID.randomUUID();
        this.email = EmailAddresses.normalize(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.platformAdmin = platformAdmin;
        this.mustChangePassword = mustChangePassword;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Platform (SUPER_ADMIN) account created with an initial password that must be changed. */
    public static UserAccount initialPlatformAdmin(String email, String passwordHash, String displayName) {
        return new UserAccount(email, passwordHash, displayName, true, true);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
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
