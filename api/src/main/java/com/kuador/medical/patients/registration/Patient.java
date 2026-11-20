package com.kuador.medical.patients.registration;

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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Administrative record of a patient of the current clinic (the clinical record is another
 * epic). The document type + number is unique in the clinic. Tenant-scoped (ADR-007). The
 * account of the patient lives in identity and is referenced only by id.
 */
@Entity
@Table(name = "patients_patient")
class Patient {

    /** Age of majority of the spec of MEDK-12. */
    static final int MAJORITY_AGE = 18;

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

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String sex;

    private @Nullable String email;

    private @Nullable String phone;

    private @Nullable String address;

    @Column(name = "adult_since")
    private @Nullable LocalDate adultSince;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PatientOrigin origin;

    @Column(name = "account_ref")
    private @Nullable UUID accountRef;

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

    protected Patient() {}

    Patient(UUID id, PatientData data, PatientOrigin origin, @Nullable UUID createdBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.origin = Objects.requireNonNull(origin, "origin");
        apply(data);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    /** Whether the patient is under 18 on {@code today} (the date of the clinic). */
    static boolean isMinor(LocalDate birthDate, LocalDate today) {
        return birthDate.plusYears(MAJORITY_AGE).isAfter(today);
    }

    boolean isMinor(LocalDate today) {
        return isMinor(birthDate, today);
    }

    void update(PatientData data, UUID actor) {
        apply(data);
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    /** Marks the patient as adult since {@code date}; a patient already marked keeps its date. */
    void markAdult(LocalDate date) {
        if (adultSince == null) {
            this.adultSince = Objects.requireNonNull(date, "date");
            this.updatedAt = Instant.now();
        }
    }

    void linkAccount(UUID accountId) {
        this.accountRef = Objects.requireNonNull(accountId, "accountId");
    }

    void ensureVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The patient was modified by someone else.");
        }
    }

    /** The current data, to be changed by a partial update. */
    PatientData data() {
        return new PatientData(documentType, documentNumber, firstNames, lastNames, birthDate, sex, email, phone, address);
    }

    private void apply(PatientData data) {
        this.documentType = data.documentType();
        this.documentNumber = data.documentNumber().strip();
        this.firstNames = data.firstNames().strip();
        this.lastNames = data.lastNames().strip();
        this.birthDate = data.birthDate();
        this.sex = data.sex();
        this.email = data.email() == null || data.email().isBlank() ? null : data.email().strip();
        this.phone = blankToNull(data.phone());
        this.address = blankToNull(data.address());
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getDocumentType() {
        return documentType;
    }

    String getDocumentNumber() {
        return documentNumber;
    }

    String getFirstNames() {
        return firstNames;
    }

    String getLastNames() {
        return lastNames;
    }

    LocalDate getBirthDate() {
        return birthDate;
    }

    String getSex() {
        return sex;
    }

    @Nullable String getEmail() {
        return email;
    }

    @Nullable String getPhone() {
        return phone;
    }

    @Nullable String getAddress() {
        return address;
    }

    @Nullable LocalDate getAdultSince() {
        return adultSince;
    }

    PatientOrigin getOrigin() {
        return origin;
    }

    @Nullable UUID getAccountRef() {
        return accountRef;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    long getVersion() {
        return version;
    }
}
