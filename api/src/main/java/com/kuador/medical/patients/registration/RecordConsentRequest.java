package com.kuador.medical.patients.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /patients/{id}/consents}. */
record RecordConsentRequest(
        @Nullable UUID id, @NotBlank @Size(max = 20) String textVersion, @NotNull ConsentGrantor grantedBy) {}
