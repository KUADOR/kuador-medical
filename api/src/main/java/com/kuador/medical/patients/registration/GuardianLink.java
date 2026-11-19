package com.kuador.medical.patients.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Link between a patient and a guardian, in force from {@code validFrom} until it ends. An ended
 * link is kept as history without access (plan of MEDK-12). Tenant-scoped (ADR-007).
 */
@Entity
@Table(name = "patients_guardian_link")
class GuardianLink {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "guardian_id", nullable = false, updatable = false)
    private UUID guardianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, updatable = false)
    private LinkType linkType;

    @Column(nullable = false, updatable = false)
    private String relationship;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private @Nullable Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private @Nullable LinkEndReason endReason;

    @Column(name = "authorized_by", updatable = false)
    private @Nullable UUID authorizedBy;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    protected GuardianLink() {}

    private GuardianLink(UUID id, UUID patientId, UUID guardianId, LinkType linkType, String relationship, @Nullable UUID createdBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.patientId = patientId;
        this.guardianId = guardianId;
        this.linkType = linkType;
        this.relationship = relationship.strip();
        this.validFrom = Instant.now();
        this.createdBy = createdBy;
    }

    /** A legal guardian of a minor, added by the staff. */
    static GuardianLink legalGuardian(UUID id, UUID patientId, UUID guardianId, String relationship, @Nullable UUID createdBy) {
        return new GuardianLink(id, patientId, guardianId, LinkType.LEGAL_GUARDIAN, relationship, createdBy);
    }

    boolean isCurrent() {
        return validTo == null;
    }

    /** Ends the link; ending an ended link changes nothing. */
    void end(LinkEndReason reason) {
        if (validTo == null) {
            this.validTo = Instant.now();
            this.endReason = reason;
        }
    }

    UUID getId() {
        return id;
    }

    UUID getPatientId() {
        return patientId;
    }

    UUID getGuardianId() {
        return guardianId;
    }

    LinkType getLinkType() {
        return linkType;
    }

    String getRelationship() {
        return relationship;
    }

    Instant getValidFrom() {
        return validFrom;
    }

    @Nullable Instant getValidTo() {
        return validTo;
    }

    @Nullable LinkEndReason getEndReason() {
        return endReason;
    }

    @Nullable UUID getAuthorizedBy() {
        return authorizedBy;
    }
}
