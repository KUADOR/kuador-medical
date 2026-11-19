package com.kuador.medical.patients.registration;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

/**
 * Persists and flushes records of the patients module, so the database decides races: a
 * duplicated document is {@code 409 PATIENT_DUPLICATE} and an id already used (client-generated
 * ids) is {@code 409 RESOURCE_ID_TAKEN}.
 */
@Component
class PatientStore {

    private static final Map<String, ErrorCode> CONSTRAINTS = Map.of(
            "patients_patient_pkey", ErrorCode.RESOURCE_ID_TAKEN,
            "patients_consent_pkey", ErrorCode.RESOURCE_ID_TAKEN,
            "patients_guardian_link_pkey", ErrorCode.RESOURCE_ID_TAKEN,
            "patients_patient_document_unique", ErrorCode.PATIENT_DUPLICATE);

    private final EntityManager entityManager;

    PatientStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void persist(Object entity) {
        entityManager.persist(entity);
        flush();
    }

    void flush() {
        try {
            entityManager.flush();
        } catch (PersistenceException e) {
            if (e instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                ErrorCode code = CONSTRAINTS.get(violation.getConstraintName());
                if (code == ErrorCode.PATIENT_DUPLICATE) {
                    throw duplicate();
                }
                if (code == ErrorCode.RESOURCE_ID_TAKEN) {
                    throw ApiException.conflict(code, "The id is already used by another resource.");
                }
            }
            throw e;
        }
    }

    static ApiException duplicate() {
        return ApiException.conflict(ErrorCode.PATIENT_DUPLICATE, "A patient with the same document exists in the clinic.");
    }
}
