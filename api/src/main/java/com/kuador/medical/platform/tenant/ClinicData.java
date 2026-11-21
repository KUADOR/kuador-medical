package com.kuador.medical.platform.tenant;

import org.jspecify.annotations.Nullable;

/**
 * Data of a clinic kept by the platform (FR-007). Personal and tax data: never logged.
 * Fields are non-null for onboarded clinics except {@code tradeName} and {@code mspEstablishmentNumber}.
 */
record ClinicData(
        String legalName,
        @Nullable String tradeName,
        String ruc,
        ClinicType clinicType,
        String countryCode,
        String city,
        String address,
        String phone,
        String contactEmail,
        @Nullable String mspEstablishmentNumber) {

    @Override
    public String toString() {
        return "ClinicData[redacted]";
    }
}
