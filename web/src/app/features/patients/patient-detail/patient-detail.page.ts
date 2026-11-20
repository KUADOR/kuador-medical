import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  KdAlert,
  KdButton,
  KdCard,
  KdErrorState,
  KdField,
  KdIcon,
  KdInput,
  KdLoadingState,
} from '../../../design-system';
import type { Patient, PatientInvitationRequest } from '../../../core/api/api-types';
import { CONSENT_TEXT_VERSION } from '../../../core/catalogs/patient-catalogs';
import { errorMessage, toProblem } from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  ACCOUNT_STATUS_LABELS,
  CONSENT_GRANTOR_LABELS,
  ORIGIN_LABELS,
  documentTypeLabel,
  formatDate,
  fullName,
  sexLabel,
} from '../../../core/patients/patient-labels';
import { PatientsService, newAttempt } from '../../../core/patients/patients.service';
import { AppRoutes, editPatientUrl } from '../../../core/session/navigation';
import { SessionStore } from '../../../core/session/session.store';
import { PatientGuardianLinks } from './patient-guardian-links';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; patient: Patient }
  | { kind: 'failed'; message: string; notFound: boolean };

type Channel = PatientInvitationRequest['channel'];

/** The one action of the view filled with the accent (DESIGN.md R-02). */
type PrimaryAction = 'consent' | 'invite' | null;

/** Accounts that can (re)receive an activation invitation. */
const INVITABLE: readonly Patient['accountStatus'][] = ['NONE', 'INVITED'];

/**
 * Administrative data of a patient (/patients/:id): consent, account invitation and guardian
 * links. Each action shows only with its permission; the API enforces it again.
 * Built with the design system only (MEDK-43): same data and actions as before the redesign.
 */
@Component({
  selector: 'mk-patient-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    FormsModule,
    PatientGuardianLinks,
    KdAlert,
    KdButton,
    KdCard,
    KdErrorState,
    KdField,
    KdIcon,
    KdInput,
    KdLoadingState,
  ],
  templateUrl: './patient-detail.page.html',
  styleUrl: './patient-detail.page.scss',
})
export class PatientDetailPage implements OnInit {
  private readonly patients = inject(PatientsService);
  private readonly session = inject(SessionStore);

  /** Route parameter :id. */
  readonly id = input.required<string>();

  protected readonly patientsLink = AppRoutes.patients;
  protected readonly consentVersion = CONSENT_TEXT_VERSION;
  protected readonly fullName = fullName;
  protected readonly formatDate = formatDate;
  protected readonly documentTypeLabel = documentTypeLabel;
  protected readonly sexLabel = sexLabel;
  protected readonly accountStatusLabels = ACCOUNT_STATUS_LABELS;
  protected readonly originLabels = ORIGIN_LABELS;
  protected readonly grantorLabels = CONSENT_GRANTOR_LABELS;

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  protected readonly patient = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.patient : null;
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current : null;
  });

  private readonly permissions = this.session.permissions;
  protected readonly canEdit = computed(() => this.permissions().has('patients.edit'));
  protected readonly canRegister = computed(() => this.permissions().has('patients.register'));
  protected readonly canManageGuardians = computed(() =>
    this.permissions().has('patients.guardians.manage'),
  );
  protected readonly editLink = computed(() => editPatientUrl(this.id()));

  /** Consent missing, revoked or for an older text version: offer to record the current one. */
  protected readonly consentOutdated = computed(() => {
    const consent = this.patient()?.currentConsent;
    return !consent || !!consent.revokedAt || consent.textVersion !== CONSENT_TEXT_VERSION;
  });
  protected readonly canInvite = computed(() => {
    const patient = this.patient();
    return !!patient && !patient.minor && INVITABLE.includes(patient.accountStatus);
  });

  /** The add guardian form is open: its submit is the primary action then. */
  protected readonly addingGuardian = signal(false);

  /**
   * Pending work first: a missing or outdated consent, then the account invitation. Nothing is
   * filled with the accent when there is nothing pending.
   */
  protected readonly primaryAction = computed<PrimaryAction>(() => {
    if (this.addingGuardian() || !this.canRegister()) {
      return null;
    }
    if (this.consentOutdated()) {
      return 'consent';
    }
    return this.canInvite() ? 'invite' : null;
  });

  // ---- consent
  protected readonly consentGrantedBy = signal<'PATIENT' | 'GUARDIAN'>('PATIENT');
  protected readonly recordingConsent = signal(false);
  protected readonly consentFailure = signal<string | null>(null);
  private consentAttempt = newAttempt();

  // ---- account invitation
  protected readonly channel = signal<Channel>('EMAIL');
  protected readonly inviting = signal(false);
  protected readonly inviteFailure = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.patients.get(this.id()).subscribe({
      next: (patient) => {
        this.state.set({ kind: 'ready', patient });
        this.consentGrantedBy.set(patient.minor ? 'GUARDIAN' : 'PATIENT');
        this.channel.set(!patient.email && patient.phone ? 'SMS' : 'EMAIL');
      },
      error: (error: unknown) =>
        this.state.set({
          kind: 'failed',
          message: errorMessage(error),
          notFound: toProblem(error)?.code === 'NOT_FOUND',
        }),
    });
  }

  protected recordConsent(): void {
    const patient = this.patient();
    if (!patient || this.recordingConsent()) {
      return;
    }
    const attempt = this.consentAttempt;
    const grantedBy = patient.minor ? 'GUARDIAN' : this.consentGrantedBy();
    this.recordingConsent.set(true);
    this.consentFailure.set(null);
    this.notice.set(null);
    this.patients
      .recordConsent(
        patient.id,
        { id: attempt.id, textVersion: CONSENT_TEXT_VERSION, grantedBy },
        attempt.key,
      )
      .subscribe({
        next: (consent) => {
          this.recordingConsent.set(false);
          this.consentAttempt = newAttempt();
          this.state.set({ kind: 'ready', patient: { ...patient, currentConsent: consent } });
          this.notice.set('Se registró el consentimiento de datos personales.');
        },
        error: (error: unknown) => {
          this.recordingConsent.set(false);
          if (!(error instanceof HttpErrorResponse && error.status === 0)) {
            this.consentAttempt = newAttempt();
          }
          this.consentFailure.set(errorMessage(error));
        },
      });
  }

  protected invite(): void {
    const patient = this.patient();
    if (!patient || this.inviting()) {
      return;
    }
    this.inviting.set(true);
    this.inviteFailure.set(null);
    this.notice.set(null);
    const channel = this.channel();
    this.patients.inviteAccount(patient.id, { channel }).subscribe({
      next: () => {
        this.inviting.set(false);
        this.state.set({ kind: 'ready', patient: { ...patient, accountStatus: 'INVITED' } });
        this.notice.set(
          channel === 'EMAIL'
            ? 'Se envió la invitación al correo registrado del paciente.'
            : 'Se envió la invitación al teléfono registrado del paciente.',
        );
      },
      error: (error: unknown) => {
        this.inviting.set(false);
        this.inviteFailure.set(errorMessage(error));
      },
    });
  }
}
