import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import type {
  ConsentGrantor,
  Patient,
  RegisterPatientRequest,
  UpdatePatientRequest,
} from '../../../core/api/api-types';
import {
  CONSENT_TEXT_VERSION,
  DOCUMENT_TYPES,
  SEX_OPTIONS,
} from '../../../core/catalogs/patient-catalogs';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdFieldGroup,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../../design-system';
import {
  applyServerErrors,
  controlErrorMessage,
  e164Phone,
  notBlank,
  notInFuture,
} from '../../../core/forms/form-errors';
import { NoticeService } from '../../../core/notice/notice.service';
import { MAJORITY_AGE, fullName, isMinor } from '../../../core/patients/patient-labels';
import { PatientsService, newAttempt } from '../../../core/patients/patients.service';
import { AppRoutes, patientUrl } from '../../../core/session/navigation';
import {
  GuardianPersonFields,
  guardianPersonGroup,
  optional,
  toGuardianPerson,
} from '../shared/guardian-person-fields';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; patient: Patient | null }
  | { kind: 'failed'; message: string; notFound: boolean };

type Field =
  | 'documentType'
  | 'documentNumber'
  | 'firstNames'
  | 'lastNames'
  | 'birthDate'
  | 'sex'
  | 'email'
  | 'phone'
  | 'address'
  | 'consent'
  | 'consentGrantedBy';

/** Fields of PATCH /patients/{id}: only the changed ones are sent. */
const EDITABLE_FIELDS = [
  'documentType',
  'documentNumber',
  'firstNames',
  'lastNames',
  'birthDate',
  'sex',
  'email',
  'phone',
  'address',
] as const;

/**
 * Register (/patients/new) or edit (/patients/:id/edit) a patient. Registration asks for the
 * mandatory data, the LOPDP consent and, for a minor, the legal guardian (FR-008 to FR-010).
 */
@Component({
  selector: 'mk-patient-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    GuardianPersonFields,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdFieldGroup,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './patient-form.page.html',
  styleUrl: './patient-form.page.scss',
})
export class PatientFormPage implements OnInit {
  private readonly patients = inject(PatientsService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Route parameter :id, absent on /patients/new. */
  readonly id = input<string | undefined>();

  protected readonly patientsLink = AppRoutes.patients;
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly sexOptions = SEX_OPTIONS;
  protected readonly consentVersion = CONSENT_TEXT_VERSION;
  protected readonly majorityAge = MAJORITY_AGE;

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly versionConflict = signal(false);

  protected readonly isNew = computed(() => !this.id());
  protected readonly patient = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.patient : null;
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current : null;
  });
  protected readonly heading = computed(() => {
    const patient = this.patient();
    if (this.isNew()) {
      return 'Registrar paciente';
    }
    return patient ? `Editar paciente ${fullName(patient)}` : 'Editar paciente';
  });
  protected readonly cancelLink = computed(() => {
    const id = this.id();
    return id ? patientUrl(id) : AppRoutes.patients;
  });

  /** Same client id and Idempotency-Key while retrying after a network error. */
  private attempt = newAttempt();

  protected readonly form = this.fb.group({
    documentType: [DOCUMENT_TYPES[0]?.code ?? '', [Validators.required]],
    documentNumber: ['', [Validators.required, Validators.maxLength(30), notBlank]],
    firstNames: ['', [Validators.required, Validators.maxLength(120), notBlank]],
    lastNames: ['', [Validators.required, Validators.maxLength(120), notBlank]],
    birthDate: ['', [Validators.required, notInFuture]],
    sex: ['', [Validators.required]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.maxLength(20), e164Phone]],
    address: ['', [Validators.maxLength(250)]],
    consent: [false, [Validators.requiredTrue]],
    consentGrantedBy: ['PATIENT' as ConsentGrantor, [Validators.required]],
    guardian: this.fb.group({
      relationship: ['', [Validators.required, Validators.maxLength(60), notBlank]],
      person: guardianPersonGroup(this.fb),
    }),
  });

  private readonly birthDate = toSignal(
    this.form.controls.birthDate.valueChanges.pipe(startWith(this.form.controls.birthDate.value)),
    { requireSync: true },
  );
  /** Minor by the browser date; the API decides with the clinic time zone. */
  protected readonly minor = computed(() => isMinor(this.birthDate()) === true);

  /** Re-renders the field errors when the form is touched or the server sets errors. */
  private readonly formEvents = toSignal(this.form.events);

  ngOnInit(): void {
    if (this.isNew()) {
      this.applyMinorRules();
      this.form.controls.birthDate.valueChanges.subscribe(() => this.applyMinorRules());
      this.state.set({ kind: 'ready', patient: null });
    } else {
      this.form.controls.consent.disable();
      this.form.controls.consentGrantedBy.disable();
      this.form.controls.guardian.disable();
      this.load();
    }
  }

  protected load(): void {
    const id = this.id();
    if (!id) {
      return;
    }
    this.state.set({ kind: 'loading' });
    this.failure.set(null);
    this.versionConflict.set(false);
    this.patients.get(id).subscribe({
      next: (patient) => {
        this.form.patchValue({
          documentType: patient.documentType,
          documentNumber: patient.documentNumber,
          firstNames: patient.firstNames,
          lastNames: patient.lastNames,
          birthDate: patient.birthDate,
          sex: patient.sex,
          email: patient.email ?? '',
          phone: patient.phone ?? '',
          address: patient.address ?? '',
        });
        this.form.markAsPristine();
        this.form.markAsUntouched();
        this.state.set({ kind: 'ready', patient });
      },
      error: (error: unknown) =>
        this.state.set({
          kind: 'failed',
          message: errorMessage(error),
          notFound: toProblem(error)?.code === 'NOT_FOUND',
        }),
    });
  }

  protected error(name: Field): string | null {
    this.formEvents();
    const control = this.form.controls[name];
    if (!control.touched) {
      return null;
    }
    if (name === 'consent' && control.hasError('required')) {
      return 'Sin el consentimiento de tratamiento de datos personales no se puede registrar al paciente.';
    }
    return controlErrorMessage(control);
  }

  protected relationshipError(): string | null {
    this.formEvents();
    const control = this.form.controls.guardian.controls.relationship;
    return control.touched ? controlErrorMessage(control) : null;
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || this.state().kind !== 'ready') {
      return;
    }
    this.failure.set(null);
    this.versionConflict.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusFirstError();
      return;
    }
    const patient = this.patient();
    if (patient) {
      this.save(patient);
    } else {
      this.register();
    }
  }

  private register(): void {
    const attempt = this.attempt;
    this.submitting.set(true);
    this.patients.register(this.registerRequest(attempt.id), attempt.key).subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.notices.show(`Se registró al paciente ${fullName(saved)}.`);
        void this.router.navigateByUrl(patientUrl(saved.id));
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (!(error instanceof HttpErrorResponse && error.status === 0)) {
          // The server answered: a corrected form is a new request, with a new key.
          this.attempt = newAttempt();
        }
        this.showError(error);
      },
    });
  }

  private save(patient: Patient): void {
    const changes = this.changedFields(patient);
    if (Object.keys(changes).length === 0) {
      this.notices.show('No hubo cambios que guardar.');
      void this.router.navigateByUrl(patientUrl(patient.id));
      return;
    }
    this.submitting.set(true);
    this.patients.update(patient.id, { ...changes, version: patient.version }).subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.notices.show(`Se guardaron los cambios de ${fullName(saved)}.`);
        void this.router.navigateByUrl(patientUrl(saved.id));
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showError(error);
      },
    });
  }

  private registerRequest(id: string): RegisterPatientRequest {
    const value = this.form.getRawValue();
    const minor = this.minor();
    return {
      id,
      documentType: value.documentType,
      documentNumber: value.documentNumber.trim(),
      firstNames: value.firstNames.trim(),
      lastNames: value.lastNames.trim(),
      birthDate: value.birthDate,
      sex: value.sex,
      email: optional(value.email),
      phone: optional(value.phone),
      address: optional(value.address),
      consent: {
        textVersion: CONSENT_TEXT_VERSION,
        grantedBy: minor ? 'GUARDIAN' : value.consentGrantedBy,
      },
      guardian: minor
        ? {
            relationship: value.guardian.relationship.trim(),
            person: toGuardianPerson(this.form.controls.guardian.controls.person),
          }
        : undefined,
    };
  }

  /**
   * Only the fields that differ from the loaded patient. PATCH has no way to clear an optional
   * field (the contract has no null for email, phone or address), so an emptied one is kept.
   */
  private changedFields(patient: Patient): Omit<UpdatePatientRequest, 'version'> {
    const value = this.form.getRawValue();
    const changes: Record<string, string> = {};
    for (const field of EDITABLE_FIELDS) {
      const next = value[field].trim();
      const previous = (patient[field] ?? '').trim();
      if (next !== '' && next !== previous) {
        changes[field] = next;
      }
    }
    return changes;
  }

  /** A minor needs a legal guardian and the consent comes from the guardian (FR-010). */
  private applyMinorRules(): void {
    const guardian = this.form.controls.guardian;
    const grantedBy = this.form.controls.consentGrantedBy;
    if (isMinor(this.form.controls.birthDate.value) === true) {
      guardian.enable({ emitEvent: false });
      grantedBy.setValue('GUARDIAN', { emitEvent: false });
      grantedBy.disable({ emitEvent: false });
    } else {
      guardian.disable({ emitEvent: false });
      grantedBy.enable({ emitEvent: false });
    }
  }

  private showError(error: unknown): void {
    const code = toProblem(error)?.code;
    if (code === 'PATIENT_DUPLICATE') {
      const control = this.form.controls.documentNumber;
      control.setErrors({ server: ERROR_MESSAGES.PATIENT_DUPLICATE });
      control.markAsTouched();
    } else if (code === 'VALIDATION') {
      applyServerErrors(this.form, error);
    } else if (code === 'VERSION_CONFLICT') {
      this.versionConflict.set(true);
    }
    this.failure.set(
      code === 'VERSION_CONFLICT'
        ? 'Otra persona modificó los datos de este paciente mientras los editabas. Recarga para ver los cambios.'
        : errorMessage(error),
    );
    this.focusFirstError();
  }

  private focusFirstError(): void {
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        (
          host.querySelector<HTMLElement>('[aria-invalid="true"]') ??
          host.querySelector<HTMLElement>('[data-testid="form-error"]')
        )?.focus();
      },
      { injector: this.injector },
    );
  }
}
