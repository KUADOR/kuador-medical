package com.kuador.medical.patients.registration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /patient-registration-settings}. */
record UpdateRegistrationSettingsRequest(@NotNull Boolean selfRegistrationEnabled, @NotNull @Min(0) Long version) {}
