package com.kuador.medical.patients.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * A person who represents patients of the current clinic (for example the mother of several
 * minors): unique per clinic by document. Tenant-scoped (ADR-007).
 */
@Entity
@Table(name = "patients_guardian")
class Guardian {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "document_number", nullable = false)
    private String documentNumber;

    @Column(name = "first_names", nullable = false)
    private String firstNames;

    @Column(name = "last_names", nullable = false)
    private String lastNames;

    private @Nullable String email;

    private @Nullable String phone;

    @Column(name = "account_ref")
    private @Nullable UUID accountRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected Guardian() {}

    Guardian(GuardianPersonInput person, @Nullable UUID createdBy) {
        this.id = UUID.randomUUID();
        this.documentType = person.documentType();
        this.documentNumber = person.documentNumber().strip();
        this.firstNames = person.firstNames().strip();
        this.lastNames = person.lastNames().strip();
        this.email = person.email() == null || person.email().isBlank() ? null : person.email().strip();
        this.phone = person.phone() == null || person.phone().isBlank() ? null : person.phone().strip();
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    UUID getId() {
        return id;
    }

    String getFirstNames() {
        return firstNames;
    }

    String getLastNames() {
        return lastNames;
    }
}
