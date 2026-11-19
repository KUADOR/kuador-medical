package com.kuador.medical.patients.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Consent accepted while registering a patient ({@code ConsentInput} in the contract). */
record ConsentInput(@NotBlank @Size(max = 20) String textVersion, @NotNull ConsentGrantor grantedBy) {}
