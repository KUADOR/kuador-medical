package com.kuador.medical.platform.plan;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A plan of Kuador: modules, limits and (in {@link PlanPrice}) its prices. Platform data, so it
 * carries no {@code @TenantId}. Never deleted, only archived; optimistic locking on {@code version}.
 */
@Entity
@Table(name = "plan")
class Plan {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private @Nullable String description;

    @Column(name = "is_trial", nullable = false, updatable = false)
    private boolean trial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_module", joinColumns = @JoinColumn(name = "plan_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module_code", nullable = false)
    private Set<ModuleCode> modules = new HashSet<>();

    /** Only limited values are stored: a missing entry means unlimited. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_limit", joinColumns = @JoinColumn(name = "plan_id"))
    @MapKeyColumn(name = "limit_code")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "limit_value")
    private Map<LimitCode, Integer> limits = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Plan() {}

    Plan(UUID id, String code, String name, @Nullable String description, boolean trial,
            Set<ModuleCode> modules, Map<LimitCode, @Nullable Integer> limits, Instant now) {
        this.id = id;
        this.code = code;
        this.trial = trial;
        this.status = PlanStatus.ACTIVE;
        this.createdAt = now;
        replace(name, description, modules, limits, now);
    }

    /** Replaces name, description, modules and limits; archived plans and stale versions are rejected. */
    void update(String newName, @Nullable String newDescription, Set<ModuleCode> newModules,
            Map<LimitCode, @Nullable Integer> newLimits, long expectedVersion, Instant now) {
        ensureEditable();
        if (expectedVersion != version) {
            throw versionConflict();
        }
        replace(newName, newDescription, newModules, newLimits, now);
    }

    /** Archives the plan; returns {@code false} when it already was (no-op). The trial plan is required. */
    boolean archive(Instant now) {
        if (status == PlanStatus.ARCHIVED) {
            return false;
        }
        if (trial) {
            throw PlanValidation.trialPlanRequired();
        }
        status = PlanStatus.ARCHIVED;
        updatedAt = now;
        return true;
    }

    void ensureEditable() {
        if (status == PlanStatus.ARCHIVED) {
            throw ApiException.conflict(ErrorCode.PLAN_ARCHIVED, "The plan is archived.");
        }
    }

    static ApiException versionConflict() {
        return ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The plan changed since it was read.");
    }

    private void replace(String newName, @Nullable String newDescription, Set<ModuleCode> newModules,
            Map<LimitCode, @Nullable Integer> newLimits, Instant now) {
        this.name = newName.strip();
        this.description = newDescription == null || newDescription.isBlank() ? null : newDescription.strip();
        // Only the difference is applied, so unchanged entries are not rewritten.
        this.modules.retainAll(newModules);
        this.modules.addAll(newModules);
        Map<LimitCode, Integer> limited = new EnumMap<>(LimitCode.class);
        newLimits.forEach((code, value) -> {
            if (value != null) {
                limited.put(code, value);
            }
        });
        this.limits.keySet().retainAll(limited.keySet());
        this.limits.putAll(limited);
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    @Nullable String getDescription() {
        return description;
    }

    boolean isTrial() {
        return trial;
    }

    PlanStatus getStatus() {
        return status;
    }

    /** Enabled modules, in catalog order. */
    Set<ModuleCode> getModules() {
        return modules.isEmpty() ? EnumSet.noneOf(ModuleCode.class) : EnumSet.copyOf(modules);
    }

    /** Every limit of the catalog, in catalog order; {@code null} means unlimited. */
    Map<LimitCode, @Nullable Integer> getLimits() {
        Map<LimitCode, @Nullable Integer> all = new EnumMap<>(LimitCode.class);
        for (LimitCode code : LimitCode.values()) {
            all.put(code, limits.get(code));
        }
        return all;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    long getVersion() {
        return version;
    }
}
