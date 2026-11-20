package com.kuador.medical.patients.registration;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.clinics.ClinicCalendar;
import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.AccountProvisioning;
import com.kuador.medical.identity.NewClinicAccount;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transition to adulthood of the patients of the current clinic (FR-027, FR-028; plan of MEDK-12,
 * § Transición a la mayoría de edad). For every minor whose 18th birthday is reached on the date
 * of the clinic, in its own transaction:
 *
 * <ol>
 *   <li>sets {@code adultSince} to the 18th birthday;</li>
 *   <li>ends its current {@code LEGAL_GUARDIAN} links with {@code ADULTHOOD}, kept as history: the
 *       guardian loses the access those links gave;</li>
 *   <li>invites the patient by email to activate its own account; without an email (SMS stays
 *       inactive until a provider is defined) or with the email already used in the clinic, the
 *       invitation stays pending for the staff;</li>
 *   <li>audits every step with the system as actor.</li>
 * </ol>
 *
 * It is idempotent: a patient already marked, or without a current legal guardian, is skipped.
 */
@Service
class AdulthoodTransition {

    private static final Logger log = LoggerFactory.getLogger(AdulthoodTransition.class);

    private final PatientRepository patients;
    private final GuardianLinkRepository links;
    private final PatientStore store;
    private final AccountProvisioning accounts;
    private final ClinicCalendar calendar;
    private final AuditRecorder audit;
    private final TransactionTemplate transactions;

    AdulthoodTransition(
            PatientRepository patients,
            GuardianLinkRepository links,
            PatientStore store,
            AccountProvisioning accounts,
            ClinicCalendar calendar,
            AuditRecorder audit,
            PlatformTransactionManager transactionManager) {
        this.patients = patients;
        this.links = links;
        this.store = store;
        this.accounts = accounts;
        this.calendar = calendar;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Whether someone born on {@code birthDate} is an adult on {@code today}; same rule as {@link Patient#isMinor}. */
    static boolean reachedMajority(LocalDate birthDate, LocalDate today) {
        return !Patient.isMinor(birthDate, today);
    }

    /** The date the patient became adult: its 18th birthday (February 28 for people born on February 29). */
    static LocalDate adultSince(LocalDate birthDate) {
        return birthDate.plusYears(Patient.MAJORITY_AGE);
    }

    /**
     * Latest birth date worth loading on {@code today}: one day of margin over {@code today - 18
     * years} so that people born on February 29 are loaded; {@link #reachedMajority} decides.
     */
    static LocalDate candidateCutoff(LocalDate today) {
        return today.minusYears(Patient.MAJORITY_AGE).plusDays(1);
    }

    /**
     * Processes the patients of the clinic bound in {@link TenantContext}. A failure with one
     * patient is logged (ids only) and does not stop the others.
     *
     * @return how many patients became adult
     */
    int processCurrentTenant() {
        UUID tenantId = TenantContext.current()
                .orElseThrow(() -> new IllegalStateException("The adulthood transition needs a current tenant"));
        LocalDate today = calendar.today();
        List<UUID> candidates = transactions.execute(status -> patients.findAdulthoodCandidates(candidateCutoff(today)));
        int processed = 0;
        for (UUID patientId : candidates) {
            try {
                Boolean done = transactions.execute(status -> process(tenantId, patientId, today));
                if (Boolean.TRUE.equals(done)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.error("Adulthood transition failed for patient {} of tenant {}", patientId, tenantId, e);
            }
        }
        return processed;
    }

    private boolean process(UUID tenantId, UUID patientId, LocalDate today) {
        Patient patient = patients.lockInCurrentTenant(patientId).orElse(null);
        // Checked again under the lock: another run or an edit of the birth date may have won.
        if (patient == null || patient.getAdultSince() != null || !reachedMajority(patient.getBirthDate(), today)) {
            return false;
        }
        List<GuardianLink> legalGuardians = links.findCurrent(patientId, LinkType.LEGAL_GUARDIAN);
        if (legalGuardians.isEmpty()) {
            return false;
        }
        AuditTarget target = AuditTarget.patient(patientId);
        patient.markAdult(adultSince(patient.getBirthDate()));
        legalGuardians.forEach(link -> link.end(LinkEndReason.ADULTHOOD));
        store.flush();
        audit.record(AuditAction.PATIENT_BECAME_ADULT, tenantId, null, target);
        legalGuardians.forEach(link -> audit.record(AuditAction.GUARDIAN_LINK_ENDED, tenantId, null, target));
        invite(tenantId, patient, target);
        return true;
    }

    private void invite(UUID tenantId, Patient patient, AuditTarget target) {
        String email = patient.getEmail();
        if (patient.getAccountRef() != null || email == null || accounts.isEmailRegistered(email)) {
            audit.record(AuditAction.ADULTHOOD_INVITATION_PENDING, tenantId, null, target);
            return;
        }
        UUID accountId = accounts.inviteAccount(new NewClinicAccount(
                AccountKind.PATIENT, email, patient.getFirstNames(), patient.getLastNames(), patient.getId()));
        patient.linkAccount(accountId);
        store.flush();
        audit.record(AuditAction.PATIENT_ACCOUNT_INVITED, tenantId, null, target);
    }
}
