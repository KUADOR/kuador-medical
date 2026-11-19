package com.kuador.medical.patients.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /patients}; {@code id} is an optional client-generated id (offline-ready). */
record RegisterPatientRequest(
        @Nullable UUID id,
        @NotBlank @Pattern(regexp = Requests.DOCUMENT_TYPE) String documentType,
        @NotBlank @Size(max = 30) String documentNumber,
        @NotBlank @Size(max = 120) String firstNames,
        @NotBlank @Size(max = 120) String lastNames,
        @NotNull LocalDate birthDate,
        @NotBlank @Pattern(regexp = Requests.SEX) String sex,
        @Nullable @Email @Size(max = 254) String email,
        @Nullable @Pattern(regexp = Requests.PHONE) String phone,
        @Nullable @Size(max = 250) String address,
        @NotNull @Valid ConsentInput consent,
        @Nullable @Valid GuardianInput guardian) {

    PatientData data() {
        return new PatientData(documentType, documentNumber, firstNames, lastNames, birthDate, sex, email, phone, address);
    }

    @Override
    public String toString() {
        return "RegisterPatientRequest[redacted]";
    }
}
