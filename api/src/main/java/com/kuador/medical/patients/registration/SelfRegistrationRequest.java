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

/** Body of {@code POST /public/clinics/{publicCode}/patient-registrations}. */
record SelfRegistrationRequest(
        @Nullable UUID id,
        @NotBlank @Pattern(regexp = Requests.DOCUMENT_TYPE) String documentType,
        @NotBlank @Size(max = 30) String documentNumber,
        @NotBlank @Size(max = 120) String firstNames,
        @NotBlank @Size(max = 120) String lastNames,
        @NotNull LocalDate birthDate,
        @NotBlank @Pattern(regexp = Requests.SEX) String sex,
        @NotBlank @Email @Size(max = 254) String email,
        @Nullable @Pattern(regexp = Requests.PHONE) String phone,
        @Nullable @Size(max = 250) String address,
        @NotBlank @Size(max = 128) String password,
        @NotNull @Valid SelfConsent consent) {

    /** The consent of a self-registration is always granted by the patient. */
    record SelfConsent(@NotBlank @Size(max = 20) String textVersion) {}

    PatientData data() {
        return new PatientData(documentType, documentNumber, firstNames, lastNames, birthDate, sex, email, phone, address);
    }

    /** The request without the password, used to recognize a retried {@code Idempotency-Key}. */
    SelfRegistrationRequest withoutPassword() {
        return new SelfRegistrationRequest(
                id, documentType, documentNumber, firstNames, lastNames, birthDate, sex, email, phone, address, "", consent);
    }

    @Override
    public String toString() {
        return "SelfRegistrationRequest[redacted]";
    }
}
