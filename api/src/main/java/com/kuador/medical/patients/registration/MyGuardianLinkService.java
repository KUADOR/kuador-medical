package com.kuador.medical.patients.registration;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.clinics.ClinicCalendar;
import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.AccountProvisioning;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.ClinicAccountInfo;
import com.kuador.medical.patients.registration.Responses.GuardianLinkResponse;
import com.kuador.medical.shared.idempotency.IdempotentCreations;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links of the patient behind the calling account (MEDK-18, FR-028): the adult patient sees its
 * current and historical links and explicitly authorizes an emergency contact or a delegated
 * access. Only {@code PATIENT} accounts of the current clinic may use it; others get {@code 403}.
 */
@Service
class MyGuardianLinkService {

    static final String AUTHORIZE_OPERATION = "authorizeMyGuardianLink";

    private final PatientRepository patients;
    private final GuardianRepository guardians;
    private final GuardianLinkRepository links;
    private final PatientStore store;
    private final IdempotentCreations idempotency;
    private final AccountProvisioning accounts;
    private final ClinicCalendar calendar;
    private final AuditRecorder audit;

    MyGuardianLinkService(
            PatientRepository patients,
            GuardianRepository guardians,
            GuardianLinkRepository links,
            PatientStore store,
            IdempotentCreations idempotency,
            AccountProvisioning accounts,
            ClinicCalendar calendar,
            AuditRecorder audit) {
        this.patients = patients;
        this.guardians = guardians;
        this.links = links;
        this.store = store;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.calendar = calendar;
        this.audit = audit;
    }

    @Transactional
    List<GuardianLinkResponse> list(AuthenticatedAccount caller) {
        UUID patientId = patientOf(caller);
        List<GuardianLink> found = links.findByPatient(patientId);
        Map<UUID, Guardian> byId = guardians.findAllInCurrentTenant(
                        found.stream().map(GuardianLink::getGuardianId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Guardian::getId, Function.identity()));
        audit.record(AuditAction.PATIENT_VIEWED, caller.tenantId(), caller.accountId(), AuditTarget.patient(patientId));
        return found.stream().map(link -> GuardianLinkResponse.of(link, byId.get(link.getGuardianId()))).toList();
    }

    @Transactional
    GuardianLinkResponse authorize(AuthorizeLinkRequest request, @Nullable String idempotencyKey, AuthenticatedAccount caller) {
        UUID patientId = patientOf(caller);
        Patient patient = patients.lockInCurrentTenant(patientId).orElseThrow(ApiException::notFound);
        if (patient.isMinor(calendar.today())) {
            throw ApiException.forbidden("Only an adult patient authorizes its links.");
        }
        Map<String, Object> scoped = new LinkedHashMap<>();
        scoped.put("account", caller.accountId());
        scoped.put("request", request);
        IdempotentCreations.Outcome outcome = idempotency.create(AUTHORIZE_OPERATION, idempotencyKey, scoped, () -> {
            Guardian guardian = guardian(request, caller);
            GuardianLink link = GuardianLink.authorizedByPatient(
                    request.id() != null ? request.id() : UUID.randomUUID(), patientId, guardian.getId(),
                    request.linkType().linkType(), request.relationship(), caller.accountId());
            store.persist(link);
            audit.record(AuditAction.GUARDIAN_LINK_AUTHORIZED, caller.tenantId(), caller.accountId(), AuditTarget.patient(patientId));
            return link.getId();
        });
        GuardianLink link = links.findOfPatient(outcome.resourceId(), patientId).orElseThrow(ApiException::notFound);
        return GuardianLinkResponse.of(link, guardians.findInCurrentTenant(link.getGuardianId()).orElseThrow());
    }

    /** The patient of the calling account; {@code 403} for any account that is not a patient of this clinic. */
    private UUID patientOf(AuthenticatedAccount caller) {
        ClinicAccountInfo account = caller.tenantId() == null ? null : accounts.find(caller.accountId()).orElse(null);
        if (account == null || account.kind() != AccountKind.PATIENT || account.patientRef() == null) {
            throw ApiException.forbidden("Only a patient account has guardian links.");
        }
        return account.patientRef();
    }

    private Guardian guardian(AuthorizeLinkRequest request, AuthenticatedAccount caller) {
        List<FieldViolation> violations = new ArrayList<>();
        if (request.guardianId() == null && request.person() == null) {
            violations.add(new FieldViolation("person", "REQUIRED"));
        } else if (request.guardianId() != null && request.person() != null) {
            violations.add(new FieldViolation("guardianId", "EXCLUSIVE_WITH_PERSON"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        if (request.guardianId() != null) {
            return guardians.findInCurrentTenant(request.guardianId()).orElseThrow(() -> ApiException.validation(
                    List.of(new FieldViolation("guardianId", "UNKNOWN_GUARDIAN"))));
        }
        GuardianPersonInput person = request.person();
        return guardians.findByDocument(person.documentType(), person.documentNumber().strip()).orElseGet(() -> {
            Guardian created = new Guardian(person, caller.accountId());
            store.persist(created);
            return created;
        });
    }
}
