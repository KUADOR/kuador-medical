package com.kuador.medical.patients.registration;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.clinics.ClinicCalendar;
import com.kuador.medical.identity.AccountProvisioning;
import com.kuador.medical.identity.NewSelfRegisteredAccount;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.notifications.EmailMessage;
import com.kuador.medical.notifications.NotificationSender;
import com.kuador.medical.shared.idempotency.IdempotentCreations;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optional self-registration of adult patients (MEDK-17): only in clinics that enabled it (else
 * {@code 404}), with the same mandatory data and consent as the staff registration, and an
 * account that becomes active only after its email is verified. A document or email already
 * registered in the clinic is not revealed by the response ({@code 202} anyway): the person is
 * told by email instead.
 */
@Service
class SelfRegistrationService {

    static final String OPERATION = "selfRegisterPatient";
    private static final Logger log = LoggerFactory.getLogger(SelfRegistrationService.class);

    private final RegistrationSettingsService settings;
    private final PatientRepository patients;
    private final PatientStore store;
    private final IdempotentCreations idempotency;
    private final AccountProvisioning accounts;
    private final TenantDirectory tenants;
    private final NotificationSender notifications;
    private final ClinicCalendar calendar;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;

    SelfRegistrationService(
            RegistrationSettingsService settings,
            PatientRepository patients,
            PatientStore store,
            IdempotentCreations idempotency,
            AccountProvisioning accounts,
            TenantDirectory tenants,
            NotificationSender notifications,
            ClinicCalendar calendar,
            AuditRecorder audit,
            TransactionTemplate transaction) {
        this.settings = settings;
        this.patients = patients;
        this.store = store;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.tenants = tenants;
        this.notifications = notifications;
        this.calendar = calendar;
        this.audit = audit;
        this.transaction = transaction;
    }

    void register(String publicCode, SelfRegistrationRequest request, @Nullable String idempotencyKey) {
        UUID tenantId = settings.enabledClinic(publicCode).orElseThrow(ApiException::notFound);
        TenantContext.runAs(tenantId, () -> transaction.executeWithoutResult(status -> {
            validate(request);
            idempotency.create(OPERATION, idempotencyKey, request.withoutPassword(), () -> create(tenantId, request));
        }));
    }

    private void validate(SelfRegistrationRequest request) {
        LocalDate today = calendar.today();
        List<FieldViolation> violations = new ArrayList<>();
        if (request.birthDate().isAfter(today)) {
            violations.add(new FieldViolation("birthDate", "FUTURE_DATE"));
        } else if (Patient.isMinor(request.birthDate(), today)) {
            violations.add(new FieldViolation("birthDate", "MINOR_NOT_ALLOWED"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        accounts.checkFirstPassword(request.password());
    }

    private UUID create(UUID tenantId, SelfRegistrationRequest request) {
        if (patients.existsByDocument(request.documentType(), request.documentNumber().strip())
                || accounts.isEmailRegistered(request.email())) {
            afterCommit(alreadyRegistered(tenantId, request));
            // Nothing is created; the key still remembers this request.
            return UUID.randomUUID();
        }
        Patient patient = new Patient(
                request.id() != null ? request.id() : UUID.randomUUID(), request.data(), PatientOrigin.SELF, null);
        store.persist(patient);
        store.persist(new Consent(UUID.randomUUID(), patient.getId(), request.consent().textVersion(),
                ConsentGrantor.PATIENT, ConsentChannel.SELF, null, null));
        UUID accountId = accounts.registerPendingVerification(new NewSelfRegisteredAccount(
                request.email(), request.password(), request.firstNames(), request.lastNames(), patient.getId()));
        patient.linkAccount(accountId);
        store.flush();
        AuditTarget target = AuditTarget.patient(patient.getId());
        audit.record(AuditAction.PATIENT_SELF_REGISTERED, tenantId, null, target);
        audit.record(AuditAction.PATIENT_CONSENT_RECORDED, tenantId, null, target);
        return patient.getId();
    }

    /** Email (in Spanish) to a person whose document or email is already registered in the clinic. */
    private EmailMessage alreadyRegistered(UUID tenantId, SelfRegistrationRequest request) {
        String clinicName = tenants.find(tenantId).map(TenantDirectory.TenantInfo::name).orElse("la clínica");
        String body = """
                Hola %s:

                Recibimos una solicitud de registro como paciente de %s, pero ya existe un registro con tu documento o con este correo.

                Si ya tienes una cuenta, inicia sesión o recupera tu contraseña. Si no, comunícate con la clínica para que te ayude a activar tu acceso.

                Si no hiciste esta solicitud, ignora este correo.
                """.formatted(request.firstNames().strip(), clinicName);
        return new EmailMessage(request.email().strip(), "Tu registro en %s".formatted(clinicName), body);
    }

    private void afterCommit(EmailMessage email) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notifications.sendEmail(email);
                } catch (RuntimeException e) {
                    // No email address nor name in the log (Constitution V).
                    log.warn("Self-registration notice could not be sent: {}", e.getClass().getSimpleName());
                }
            }
        });
    }
}
