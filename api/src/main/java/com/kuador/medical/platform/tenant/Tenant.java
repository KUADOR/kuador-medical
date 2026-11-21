package com.kuador.medical.platform.tenant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A clinic. It is the tenant itself, so it carries no {@code @TenantId}. Clinics onboarded by the
 * SUPER_ADMIN (MEDK-66) carry their legal data and initial OWNER; clinics of the earlier
 * self-signup only have a name. Optimistic locking on {@code version}.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;

    /** Display name used by identity: the trade name, or the legal name when there is none. */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    @Column(name = "legal_name")
    private @Nullable String legalName;

    @Column(name = "trade_name")
    private @Nullable String tradeName;

    private @Nullable String ruc;

    @Enumerated(EnumType.STRING)
    @Column(name = "clinic_type")
    private @Nullable ClinicType clinicType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private @Nullable String countryCode;

    private @Nullable String city;

    private @Nullable String address;

    private @Nullable String phone;

    @Column(name = "contact_email")
    private @Nullable String contactEmail;

    @Column(name = "msp_establishment_number")
    private @Nullable String mspEstablishmentNumber;

    @Column(name = "owner_full_name")
    private @Nullable String ownerFullName;

    @Column(name = "owner_email")
    private @Nullable String ownerEmail;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private @Nullable Instant updatedAt;

    @Column(name = "purged_at")
    private @Nullable Instant purgedAt;

    @Version
    private long version;

    protected Tenant() {}

    /** Self-signup clinic (FR-015 of the identity epic): only a name. */
    Tenant(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.status = TenantStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    /** Clinic onboarded by the SUPER_ADMIN {@code createdBy}. */
    static Tenant onboard(UUID id, ClinicData data, String ownerFullName, String ownerEmail, UUID createdBy,
            Instant now) {
        Tenant tenant = new Tenant(id, displayName(data));
        tenant.createdAt = now;
        tenant.updatedAt = now;
        tenant.createdBy = createdBy;
        tenant.ownerFullName = ownerFullName;
        tenant.ownerEmail = ownerEmail;
        tenant.apply(data);
        return tenant;
    }

    /** Replaces the clinic data (the caller checks the version it read). */
    void update(ClinicData data, Instant now) {
        apply(data);
        this.name = displayName(data);
        this.updatedAt = now;
    }

    /** The clinic data as stored; only meaningful for onboarded clinics. */
    ClinicData data() {
        return new ClinicData(legalName, tradeName, ruc, clinicType, countryCode, city, address, phone, contactEmail,
                mspEstablishmentNumber);
    }

    private void apply(ClinicData data) {
        this.legalName = data.legalName();
        this.tradeName = data.tradeName();
        this.ruc = data.ruc();
        this.clinicType = data.clinicType();
        this.countryCode = data.countryCode();
        this.city = data.city();
        this.address = data.address();
        this.phone = data.phone();
        this.contactEmail = data.contactEmail();
        this.mspEstablishmentNumber = data.mspEstablishmentNumber();
    }

    private static String displayName(ClinicData data) {
        return data.tradeName() != null ? data.tradeName() : data.legalName();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    @Nullable String getOwnerFullName() {
        return ownerFullName;
    }

    public @Nullable String getOwnerEmail() {
        return ownerEmail;
    }

    /** When the clinic was purged (MEDK-70); {@code null} while its data exists. */
    public @Nullable Instant getPurgedAt() {
        return purgedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    long getVersion() {
        return version;
    }
}
