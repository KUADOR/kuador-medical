package com.kuador.medical.identity.role;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Role of a clinic. Tenant-scoped (ADR-007). OWNER is immutable; ADMIN can be edited (only by
 * an OWNER, checked by {@link EscalationGuard}) but never deleted; the others are edited with
 * optimistic locking on {@code version}.
 */
@Entity
@Table(name = "identity_role")
public class Role {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private @Nullable String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_code", updatable = false)
    private @Nullable SystemRole systemCode;

    @Column(nullable = false)
    private boolean clinical;

    @Column(name = "suggested_key", updatable = false)
    private @Nullable String suggestedKey;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Role() {}

    private Role(
            UUID id,
            String name,
            @Nullable String description,
            @Nullable SystemRole systemCode,
            boolean clinical,
            @Nullable String suggestedKey) {
        this.id = id;
        this.name = name.strip();
        this.description = normalizeDescription(description);
        this.systemCode = systemCode;
        this.clinical = clinical;
        this.suggestedKey = suggestedKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** A protected role (OWNER or ADMIN) seeded when the clinic is created. */
    public static Role system(SystemRole systemRole, String name, @Nullable String description) {
        return new Role(UUID.randomUUID(), name, description, systemRole, false, systemRole.name());
    }

    /** A suggested role seeded when the clinic is created; the clinic may then change or delete it. */
    public static Role suggested(String key, String name, @Nullable String description, boolean clinical) {
        return new Role(UUID.randomUUID(), name, description, null, clinical, key);
    }

    /** A role created by the clinic; name and description are trimmed, a blank description is none. */
    public static Role custom(String name, @Nullable String description) {
        return custom(UUID.randomUUID(), name, description, false);
    }

    /** A role created by the clinic with an id chosen by the client. */
    public static Role custom(UUID id, String name, @Nullable String description, boolean clinical) {
        return new Role(id, name, description, null, clinical, null);
    }

    /**
     * Replaces name, description and clinical flag. Rejects OWNER ({@code SYSTEM_ROLE_IMMUTABLE})
     * and a version other than the current one ({@code VERSION_CONFLICT}). Always marks the role
     * as changed, so its version increments even when only its permissions change.
     */
    public void update(String newName, @Nullable String newDescription, boolean newClinical, long expectedVersion) {
        ensureUpdatable(expectedVersion);
        this.name = newName.strip();
        this.description = normalizeDescription(newDescription);
        this.clinical = newClinical;
        this.updatedAt = Instant.now();
    }

    /** Same as {@link #update(String, String, boolean, long)} keeping the clinical flag. */
    public void update(String newName, @Nullable String newDescription, long expectedVersion) {
        update(newName, newDescription, clinical, expectedVersion);
    }

    /** Same checks as {@link #update}, without changing anything. */
    public void ensureUpdatable(long expectedVersion) {
        if (systemCode == SystemRole.OWNER) {
            throw immutable();
        }
        if (expectedVersion != version) {
            throw ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The role was changed by someone else.");
        }
    }

    /** Rejects the deletion of OWNER and ADMIN ({@code SYSTEM_ROLE_IMMUTABLE}). */
    public void ensureDeletable() {
        if (isSystemRole()) {
            throw immutable();
        }
    }

    public boolean isSystemRole() {
        return systemCode != null;
    }

    public boolean isOwner() {
        return systemCode == SystemRole.OWNER;
    }

    private static ApiException immutable() {
        return ApiException.conflict(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "Protected roles cannot be changed or deleted.");
    }

    private static @Nullable String normalizeDescription(@Nullable String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable SystemRole getSystemCode() {
        return systemCode;
    }

    public boolean isClinical() {
        return clinical;
    }

    public @Nullable String getSuggestedKey() {
        return suggestedKey;
    }

    public long getVersion() {
        return version;
    }
}
