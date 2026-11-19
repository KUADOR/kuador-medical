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
 * Acceptance of a version of the personal data consent text (LOPDP). Every acceptance is kept;
 * the current consent is the latest one not revoked. Tenant-scoped (ADR-007).
 */
@Entity
@Table(name = "patients_consent")
class Consent {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "text_version", nullable = false, updatable = false)
    private String textVersion;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "granted_by", nullable = false, updatable = false)
    private ConsentGrantor grantedBy;

    @Column(name = "granted_by_user", updatable = false)
    private @Nullable UUID grantedByUser;

    @Column(name = "granted_by_guardian", updatable = false)
    private @Nullable UUID grantedByGuardian;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ConsentChannel channel;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    protected Consent() {}

    Consent(
            UUID id,
            UUID patientId,
            String textVersion,
            ConsentGrantor grantedBy,
            ConsentChannel channel,
            @Nullable UUID grantedByUser,
            @Nullable UUID grantedByGuardian) {
        this.id = Objects.requireNonNull(id, "id");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.textVersion = textVersion.strip();
        this.grantedBy = Objects.requireNonNull(grantedBy, "grantedBy");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.grantedByUser = grantedByUser;
        this.grantedByGuardian = grantedByGuardian;
        this.grantedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getPatientId() {
        return patientId;
    }

    String getTextVersion() {
        return textVersion;
    }

    Instant getGrantedAt() {
        return grantedAt;
    }

    ConsentGrantor getGrantedBy() {
        return grantedBy;
    }

    ConsentChannel getChannel() {
        return channel;
    }

    @Nullable Instant getRevokedAt() {
        return revokedAt;
    }
}
