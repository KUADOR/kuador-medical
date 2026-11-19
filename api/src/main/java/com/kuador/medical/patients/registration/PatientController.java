package com.kuador.medical.patients.registration;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.patients.registration.Responses.ConsentResponse;
import com.kuador.medical.patients.registration.Responses.GuardianLinkResponse;
import com.kuador.medical.patients.registration.Responses.PatientPage;
import com.kuador.medical.patients.registration.Responses.PatientResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** {@code /patients}: administrative registration, consent, guardians and account invitation (MEDK-17). */
@RestController
@RequestMapping("/patients")
class PatientController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PatientService patients;
    private final CurrentUser currentUser;

    PatientController(PatientService patients, CurrentUser currentUser) {
        this.patients = patients;
        this.currentUser = currentUser;
    }

    @RequiresPermission("patients.read")
    @GetMapping
    PatientPage list(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable String documentType,
            @RequestParam(required = false) @Nullable String documentNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return patients.list(new PatientService.Filter(q, documentType, documentNumber), page, size, caller());
    }

    @RequiresPermission("patients.register")
    @PostMapping
    ResponseEntity<PatientResponse> register(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody RegisterPatientRequest request) {
        PatientResponse created = patients.register(request, idempotencyKey, caller());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @RequiresPermission("patients.read")
    @GetMapping("/{id}")
    PatientResponse get(@PathVariable UUID id) {
        return patients.get(id, caller());
    }

    @RequiresPermission("patients.edit")
    @PatchMapping("/{id}")
    PatientResponse update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return patients.update(id, PatientUpdate.parse(body), caller());
    }

    @RequiresPermission("patients.register")
    @PostMapping("/{id}/consents")
    @ResponseStatus(HttpStatus.CREATED)
    ConsentResponse recordConsent(
            @PathVariable UUID id,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody RecordConsentRequest request) {
        return patients.recordConsent(id, request, idempotencyKey, caller());
    }

    @RequiresPermission("patients.read")
    @GetMapping("/{id}/guardian-links")
    List<GuardianLinkResponse> links(@PathVariable UUID id) {
        return patients.links(id, caller());
    }

    @RequiresPermission("patients.guardians.manage")
    @PostMapping("/{id}/guardian-links")
    @ResponseStatus(HttpStatus.CREATED)
    GuardianLinkResponse createLink(
            @PathVariable UUID id,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreateGuardianLinkRequest request) {
        return patients.createLink(id, request, idempotencyKey, caller());
    }

    @RequiresPermission("patients.guardians.manage")
    @PostMapping("/{id}/guardian-links/{linkId}/end")
    GuardianLinkResponse endLink(@PathVariable UUID id, @PathVariable UUID linkId) {
        return patients.endLink(id, linkId, caller());
    }

    @RequiresPermission("patients.register")
    @PostMapping("/{id}/account-invitation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void inviteAccount(@PathVariable UUID id, @Valid @RequestBody PatientInvitationRequest request) {
        patients.inviteAccount(id, request, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
