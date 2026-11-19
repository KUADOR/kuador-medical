package com.kuador.medical.patients.registration;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Administrative data of a patient: the mandatory fields of the spec plus the optional contact data. */
record PatientData(
        String documentType,
        String documentNumber,
        String firstNames,
        String lastNames,
        LocalDate birthDate,
        String sex,
        @Nullable String email,
        @Nullable String phone,
        @Nullable String address) {

    PatientData {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(firstNames, "firstNames");
        Objects.requireNonNull(lastNames, "lastNames");
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(sex, "sex");
    }

    @Override
    public String toString() {
        return "PatientData[redacted]";
    }
}
