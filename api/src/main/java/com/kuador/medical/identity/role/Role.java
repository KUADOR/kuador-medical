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
 * Role of a clinic. Tenant-scoped (ADR-007). System roles (OWNER, ADMIN) are immutable
 * (FR-008); the others are edited with optimistic locking on {@code version}.
 */
@Entity
@Table(name = "role")
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

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Role() {}

    private Role(String name, @Nullable String description, @Nullable SystemRole systemCode) {
        this.id = UUID.randomUUID();
        this.name = name.strip();
        this.description = normalizeDescription(description);
        this.systemCode = systemCode;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Role system(SystemRole systemRole) {
        return new Role(systemRole.name(), systemRole.description(), systemRole);
    }

    /** A role created by the clinic; name and description are trimmed, a blank description is none. */
    public static Role custom(String name, @Nullable String description) {
        return new Role(name, description, null);
    }

    /**
     * Replaces name and description. Rejects system roles ({@code SYSTEM_ROLE_IMMUTABLE}) and a
     * version other than the current one ({@code VERSION_CONFLICT}). Always marks the role as
     * changed, so its version increments even when only its permissions change.
     */
    public void update(String newName, @Nullable String newDescription, long expectedVersion) {
        ensureUpdatable(expectedVersion);
        this.name = newName.strip();
        this.description = normalizeDescription(newDescription);
        this.updatedAt = Instant.now();
    }

    /** Same checks as {@link #update}, without changing anything. */
    public void ensureUpdatable(long expectedVersion) {
        ensureMutable();
        if (expectedVersion != version) {
            throw ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The role was changed by someone else.");
        }
    }

    /** Rejects the deletion of a system role ({@code SYSTEM_ROLE_IMMUTABLE}). */
    public void ensureDeletable() {
        ensureMutable();
    }

    public boolean isSystemRole() {
        return systemCode != null;
    }

    private void ensureMutable() {
        if (isSystemRole()) {
            throw ApiException.conflict(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "System roles cannot be changed.");
        }
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

    public long getVersion() {
        return version;
    }
}
