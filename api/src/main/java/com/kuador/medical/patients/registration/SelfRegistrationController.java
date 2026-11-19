package com.kuador.medical.patients.registration;

import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /public/clinics/{publicCode}/patient-registrations}: optional self-registration (MEDK-17). */
@RestController
@RequestMapping("/public/clinics/{publicCode}/patient-registrations")
class SelfRegistrationController {

    private final SelfRegistrationService registrations;

    SelfRegistrationController(SelfRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PublicEndpoint
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    void register(
            @PathVariable String publicCode,
            @RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody SelfRegistrationRequest request) {
        if (!publicCode.matches("^[a-z0-9-]{3,60}$")) {
            throw com.kuador.medical.shared.web.ApiException.notFound();
        }
        registrations.register(publicCode, request, idempotencyKey);
    }
}
