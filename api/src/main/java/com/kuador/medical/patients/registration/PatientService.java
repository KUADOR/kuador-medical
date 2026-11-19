package com.kuador.medical.patients.registration;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.clinics.ClinicCalendar;
import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.AccountProvisioning;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.ClinicAccountInfo;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.NewClinicAccount;
import com.kuador.medical.patients.registration.Responses.ConsentResponse;
import com.kuador.medical.patients.registration.Responses.GuardianLinkResponse;
import com.kuador.medical.patients.registration.Responses.PatientPage;
import com.kuador.medical.patients.registration.Responses.PatientResponse;
import com.kuador.medical.shared.idempotency.IdempotentCreations;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patients of the current clinic registered by the staff (MEDK-17): mandatory data and consent
 * (LOPDP), document unique in the clinic (rule 5), minors with a current legal guardian and
 * without account (rule 4), idempotent creations (rule 7) and account invitations through
 * {@link AccountProvisioning}. Reads and changes are audited with ids only (Constitution V);
 * reads run in read-write transactions because they record their audit event.
 */
@Service
class PatientService {

    static final String REGISTER_OPERATION = "registerPatient";
    static final String CONSENT_OPERATION = "recordPatientConsent";
    static final String LINK_OPERATION = "createGuardianLink";
    private static final int MAX_PAGE_SIZE = 100;
    private static final String NO_ACCOUNT = "NONE";

    private final EntityManager entityManager;
    private final PatientRepository patients;
    private final GuardianRepository guardians;
    private final GuardianLinkRepository links;
    private final ConsentRepository consents;
    private final PatientStore store;
    private final IdempotentCreations idempotency;
    private final AccountProvisioning accounts;
    private final ClinicCalendar calendar;
    private final AuditRecorder audit;

    PatientService(
            EntityManager entityManager,
            PatientRepository patients,
            GuardianRepository guardians,
            GuardianLinkRepository links,
            ConsentRepository consents,
            PatientStore store,
            IdempotentCreations idempotency,
            AccountProvisioning accounts,
            ClinicCalendar calendar,
            AuditRecorder audit) {
        this.entityManager = entityManager;
        this.patients = patients;
        this.guardians = guardians;
        this.links = links;
        this.consents = consents;
        this.store = store;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.calendar = calendar;
        this.audit = audit;
    }

    /** Filters of the list; every one is optional. */
    record Filter(@Nullable String q, @Nullable String documentType, @Nullable String documentNumber) {}

    // ------------------------------------------------------------------ reads

    @Transactional
    PatientPage list(Filter filter, int page, int size, AuthenticatedAccount caller) {
        List<FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new FieldViolation("page", "INVALID_FORMAT"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            violations.add(new FieldViolation("size", "INVALID_FORMAT"));
        }
        if (filter.q() != null && filter.q().length() > 100) {
            violations.add(new FieldViolation("q", "TOO_LONG"));
        }
        if (filter.documentNumber() != null && filter.documentNumber().length() > 30) {
            violations.add(new FieldViolation("documentNumber", "TOO_LONG"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        Map<String, Object> parameters = new HashMap<>();
        String where = where(filter, parameters);
        TypedQuery<Long> count = entityManager.createQuery("select count(p) from Patient p" + where, Long.class);
        TypedQuery<Patient> query = entityManager.createQuery(
                "select p from Patient p" + where + " order by lower(p.lastNames), lower(p.firstNames), p.id", Patient.class);
        parameters.forEach((name, value) -> {
            count.setParameter(name, value);
            query.setParameter(name, value);
        });
        List<PatientResponse> items = query.setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(this::view).toList();
        audit.record(AuditAction.PATIENTS_LISTED, caller.tenantId(), caller.accountId(), null);
        return new PatientPage(page, size, count.getSingleResult(), items);
    }

    @Transactional
    PatientResponse get(UUID id, AuthenticatedAccount caller) {
        Patient patient = patient(id);
        audit.record(AuditAction.PATIENT_VIEWED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
        return view(patient);
    }

    @Transactional
    List<GuardianLinkResponse> links(UUID id, AuthenticatedAccount caller) {
        patient(id);
        List<GuardianLink> found = links.findByPatient(id);
        Map<UUID, Guardian> byId = guardians.findAllInCurrentTenant(
                        found.stream().map(GuardianLink::getGuardianId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Guardian::getId, Function.identity()));
        audit.record(AuditAction.PATIENT_VIEWED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
        return found.stream().map(link -> GuardianLinkResponse.of(link, byId.get(link.getGuardianId()))).toList();
    }

    // ------------------------------------------------------------------ registration

    @Transactional
    PatientResponse register(RegisterPatientRequest request, @Nullable String idempotencyKey, AuthenticatedAccount caller) {
        IdempotentCreations.Outcome outcome =
                idempotency.create(REGISTER_OPERATION, idempotencyKey, request, () -> doRegister(request, caller));
        return view(patient(outcome.resourceId()));
    }

    private UUID doRegister(RegisterPatientRequest request, AuthenticatedAccount caller) {
        LocalDate today = calendar.today();
        boolean minor = Patient.isMinor(request.birthDate(), today);
        List<FieldViolation> violations = new ArrayList<>();
        if (request.birthDate().isAfter(today)) {
            violations.add(new FieldViolation("birthDate", "FUTURE_DATE"));
        }
        if (minor) {
            if (request.guardian() == null) {
                violations.add(new FieldViolation("guardian", "REQUIRED"));
            } else {
                checkExactlyOne(request.guardian().guardianId(), request.guardian().person(), "guardian", violations);
            }
            if (request.consent().grantedBy() != ConsentGrantor.GUARDIAN) {
                violations.add(new FieldViolation("consent.grantedBy", "GUARDIAN_REQUIRED"));
            }
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        if (patients.existsByDocument(request.documentType(), request.documentNumber().strip())) {
            throw PatientStore.duplicate();
        }
        Patient patient = new Patient(
                request.id() != null ? request.id() : UUID.randomUUID(), request.data(), PatientOrigin.STAFF, caller.accountId());
        store.persist(patient);
        UUID guardianId = null;
        if (minor) {
            GuardianInput input = request.guardian();
            Guardian guardian = input.guardianId() != null
                    ? guardians.findInCurrentTenant(input.guardianId()).orElseThrow(() -> ApiException.validation(
                            List.of(new FieldViolation("guardian.guardianId", "UNKNOWN_GUARDIAN"))))
                    : guardianOf(input.person(), caller);
            guardianId = guardian.getId();
            store.persist(GuardianLink.legalGuardian(UUID.randomUUID(), patient.getId(), guardianId, input.relationship(), caller.accountId()));
        }
        store.persist(new Consent(UUID.randomUUID(), patient.getId(), request.consent().textVersion(),
                request.consent().grantedBy(), ConsentChannel.WEB, caller.accountId(), guardianId));
        AuditTarget target = AuditTarget.patient(patient.getId());
        audit.record(AuditAction.PATIENT_REGISTERED, caller.tenantId(), caller.accountId(), target);
        audit.record(AuditAction.PATIENT_CONSENT_RECORDED, caller.tenantId(), caller.accountId(), target);
        if (minor) {
            audit.record(AuditAction.GUARDIAN_LINK_CREATED, caller.tenantId(), caller.accountId(), target);
        }
        return patient.getId();
    }

    @Transactional
    PatientResponse update(UUID id, PatientUpdate update, AuthenticatedAccount caller) {
        Patient patient = patients.lockInCurrentTenant(id).orElseThrow(ApiException::notFound);
        patient.ensureVersion(update.version());
        PatientData data = update.applyTo(patient.data());
        LocalDate today = calendar.today();
        if (data.birthDate().isAfter(today)) {
            throw ApiException.validation(List.of(new FieldViolation("birthDate", "FUTURE_DATE")));
        }
        if (Patient.isMinor(data.birthDate(), today) && links.findCurrent(id, LinkType.LEGAL_GUARDIAN).isEmpty()) {
            throw ApiException.validation(List.of(new FieldViolation("guardian", "REQUIRED")));
        }
        if (patients.existsByDocumentExcept(data.documentType(), data.documentNumber().strip(), id)) {
            throw PatientStore.duplicate();
        }
        patient.update(data, caller.accountId());
        store.flush();
        audit.record(AuditAction.PATIENT_UPDATED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
        return view(patient);
    }

    @Transactional
    ConsentResponse recordConsent(
            UUID id, RecordConsentRequest request, @Nullable String idempotencyKey, AuthenticatedAccount caller) {
        Patient patient = patient(id);
        Map<String, Object> scoped = scoped(id, request);
        IdempotentCreations.Outcome outcome = idempotency.create(CONSENT_OPERATION, idempotencyKey, scoped, () -> {
            if (patient.isMinor(calendar.today()) && request.grantedBy() != ConsentGrantor.GUARDIAN) {
                throw ApiException.validation(List.of(new FieldViolation("grantedBy", "GUARDIAN_REQUIRED")));
            }
            Consent consent = new Consent(request.id() != null ? request.id() : UUID.randomUUID(), id,
                    request.textVersion(), request.grantedBy(), ConsentChannel.WEB, caller.accountId(), null);
            store.persist(consent);
            audit.record(AuditAction.PATIENT_CONSENT_RECORDED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
            return consent.getId();
        });
        return ConsentResponse.of(consents.findInCurrentTenant(outcome.resourceId()).orElseThrow(ApiException::notFound));
    }

    // ------------------------------------------------------------------ guardians

    @Transactional
    GuardianLinkResponse createLink(
            UUID id, CreateGuardianLinkRequest request, @Nullable String idempotencyKey, AuthenticatedAccount caller) {
        Patient patient = patients.lockInCurrentTenant(id).orElseThrow(ApiException::notFound);
        IdempotentCreations.Outcome outcome = idempotency.create(LINK_OPERATION, idempotencyKey, scoped(id, request), () -> {
            if (!patient.isMinor(calendar.today())) {
                throw ApiException.conflict(ErrorCode.ADULT_AUTHORIZATION_REQUIRED,
                        "Links of an adult patient are authorized by the patient.");
            }
            List<FieldViolation> violations = new ArrayList<>();
            checkExactlyOne(request.guardianId(), request.person(), "", violations);
            if (!violations.isEmpty()) {
                throw ApiException.validation(violations);
            }
            Guardian guardian = request.guardianId() != null
                    ? guardians.findInCurrentTenant(request.guardianId()).orElseThrow(ApiException::notFound)
                    : guardianOf(request.person(), caller);
            GuardianLink link = GuardianLink.legalGuardian(request.id() != null ? request.id() : UUID.randomUUID(),
                    id, guardian.getId(), request.relationship(), caller.accountId());
            store.persist(link);
            audit.record(AuditAction.GUARDIAN_LINK_CREATED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
            return link.getId();
        });
        return linkView(links.findOfPatient(outcome.resourceId(), id).orElseThrow(ApiException::notFound));
    }

    @Transactional
    GuardianLinkResponse endLink(UUID id, UUID linkId, AuthenticatedAccount caller) {
        Patient patient = patients.lockInCurrentTenant(id).orElseThrow(ApiException::notFound);
        GuardianLink link = links.findOfPatient(linkId, id).orElseThrow(ApiException::notFound);
        if (!link.isCurrent()) {
            return linkView(link);
        }
        if (link.getLinkType() == LinkType.LEGAL_GUARDIAN
                && patient.isMinor(calendar.today())
                && links.findCurrent(id, LinkType.LEGAL_GUARDIAN).size() <= 1) {
            throw ApiException.conflict(ErrorCode.LAST_LEGAL_GUARDIAN, "A minor keeps at least one legal guardian.");
        }
        link.end(LinkEndReason.MANUAL);
        store.flush();
        audit.record(AuditAction.GUARDIAN_LINK_ENDED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
        return linkView(link);
    }

    // ------------------------------------------------------------------ account

    @Transactional
    void inviteAccount(UUID id, PatientInvitationRequest request, AuthenticatedAccount caller) {
        Patient patient = patients.lockInCurrentTenant(id).orElseThrow(ApiException::notFound);
        if (patient.isMinor(calendar.today())) {
            throw ApiException.conflict(ErrorCode.MINOR_CANNOT_HAVE_ACCOUNT, "A minor patient cannot have an account.");
        }
        ClinicAccountInfo account = patient.getAccountRef() == null ? null : accounts.find(patient.getAccountRef()).orElse(null);
        if (account != null && account.status() != ClinicAccountStatus.INVITED) {
            throw ApiException.conflict(ErrorCode.ACCOUNT_ALREADY_ACTIVE, "The patient already has an account.");
        }
        // SMS stays unavailable until an SMS provider is defined ([POR DEFINIR]).
        if (request.channel() != PatientInvitationRequest.Channel.EMAIL || patient.getEmail() == null) {
            throw ApiException.conflict(ErrorCode.CONTACT_CHANNEL_UNAVAILABLE, "The channel has no contact data.");
        }
        if (account != null) {
            accounts.resendInvitation(account.id());
        } else {
            UUID accountId = accounts.inviteAccount(new NewClinicAccount(
                    AccountKind.PATIENT, patient.getEmail(), patient.getFirstNames(), patient.getLastNames(), id));
            patient.linkAccount(accountId);
            store.flush();
        }
        audit.record(AuditAction.PATIENT_ACCOUNT_INVITED, caller.tenantId(), caller.accountId(), AuditTarget.patient(id));
    }

    // ------------------------------------------------------------------ helpers

    private Patient patient(UUID id) {
        return patients.findInCurrentTenant(id).orElseThrow(ApiException::notFound);
    }

    /** The guardian of the clinic with the document of {@code person}, or a new one. */
    private Guardian guardianOf(GuardianPersonInput person, AuthenticatedAccount caller) {
        return guardians.findByDocument(person.documentType(), person.documentNumber().strip()).orElseGet(() -> {
            Guardian guardian = new Guardian(person, caller.accountId());
            store.persist(guardian);
            return guardian;
        });
    }

    private static void checkExactlyOne(
            @Nullable UUID guardianId, @Nullable GuardianPersonInput person, String prefix, List<FieldViolation> violations) {
        String field = prefix.isEmpty() ? "guardianId" : prefix + ".guardianId";
        if (guardianId == null && person == null) {
            violations.add(new FieldViolation(prefix.isEmpty() ? "person" : prefix + ".person", "REQUIRED"));
        } else if (guardianId != null && person != null) {
            violations.add(new FieldViolation(field, "EXCLUSIVE_WITH_PERSON"));
        }
    }

    /** The body of a sub-resource creation together with its patient, for the Idempotency-Key. */
    private static Map<String, Object> scoped(UUID patientId, Object request) {
        Map<String, Object> scoped = new LinkedHashMap<>();
        scoped.put("patientId", patientId);
        scoped.put("request", request);
        return scoped;
    }

    private GuardianLinkResponse linkView(GuardianLink link) {
        Guardian guardian = guardians.findInCurrentTenant(link.getGuardianId()).orElseThrow();
        return GuardianLinkResponse.of(link, guardian);
    }

    private PatientResponse view(Patient patient) {
        String accountStatus = patient.getAccountRef() == null
                ? NO_ACCOUNT
                : accounts.find(patient.getAccountRef()).map(info -> info.status().name()).orElse(NO_ACCOUNT);
        ConsentResponse consent = consents.findCurrent(patient.getId(), Limit.of(1)).stream()
                .findFirst().map(ConsentResponse::of).orElse(null);
        return new PatientResponse(
                patient.getId(),
                patient.getDocumentType(),
                patient.getDocumentNumber(),
                patient.getFirstNames(),
                patient.getLastNames(),
                patient.getBirthDate(),
                patient.getSex(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getAddress(),
                patient.isMinor(calendar.today()),
                patient.getAdultSince(),
                patient.getOrigin(),
                accountStatus,
                consent,
                patient.getCreatedAt(),
                patient.getVersion());
    }

    private static String where(Filter filter, Map<String, Object> parameters) {
        List<String> conditions = new ArrayList<>();
        if (filter.q() != null && !filter.q().isBlank()) {
            parameters.put("q", "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%");
            conditions.add("(lower(p.firstNames) like :q escape '\\' or lower(p.lastNames) like :q escape '\\'"
                    + " or lower(p.documentNumber) like :q escape '\\')");
        }
        if (filter.documentType() != null && !filter.documentType().isBlank()) {
            parameters.put("documentType", filter.documentType());
            conditions.add("p.documentType = :documentType");
        }
        if (filter.documentNumber() != null && !filter.documentNumber().isBlank()) {
            parameters.put("documentNumber", filter.documentNumber().strip());
            conditions.add("p.documentNumber = :documentNumber");
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
