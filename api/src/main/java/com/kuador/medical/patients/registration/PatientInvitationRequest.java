package com.kuador.medical.patients.registration;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /patients/{id}/account-invitation}. */
record PatientInvitationRequest(@NotNull Channel channel) {

    enum Channel {
        EMAIL,
        SMS
    }
}
