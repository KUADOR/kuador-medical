package com.kuador.medical.patients.registration;

import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.patients.registration.Responses.RegistrationSettingsResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /patient-registration-settings}: self-registration of patients, disabled by default (MEDK-17). */
@RestController
@RequestMapping("/patient-registration-settings")
class RegistrationSettingsController {

    private final RegistrationSettingsService settings;
    private final CurrentUser currentUser;

    RegistrationSettingsController(RegistrationSettingsService settings, CurrentUser currentUser) {
        this.settings = settings;
        this.currentUser = currentUser;
    }

    @RequiresPermission("patients.read")
    @GetMapping
    RegistrationSettingsResponse get() {
        return settings.get();
    }

    @RequiresPermission("patients.selfRegistration.configure")
    @PutMapping
    RegistrationSettingsResponse update(@Valid @RequestBody UpdateRegistrationSettingsRequest request) {
        return settings.update(request, currentUser.current().orElseThrow(ApiException::unauthenticated));
    }
}
