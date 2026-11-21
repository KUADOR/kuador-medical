import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import type {
  BillingPeriod,
  ClinicType,
  CreateTenantRequest,
  PlatformPlan,
} from '../../../core/api/api-types';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import { applyServerErrors, controlErrorMessage, notBlank } from '../../../core/forms/form-errors';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  PlatformCreationKey,
  PlatformTenantsService,
  newPlatformCreationKey,
} from '../../../core/platform/platform-tenants.service';
import { AppRoutes, platformTenantUrl } from '../../../core/session/navigation';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdErrorSummary,
  KdField,
  KdFieldGroup,
  KdFormError,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../../design-system';
import {
  BILLING_PERIODS,
  BILLING_PERIOD_LABELS,
  CLINIC_TYPES,
  CLINIC_TYPE_LABELS,
  COUNTRY_LABELS,
  RUC_PATTERN,
  TENANT_LIMITS,
  clinicName,
  planOptionLabel,
} from './tenant-labels';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; plans: PlatformPlan[] }
  | { kind: 'failed'; message: string };

/** Ids and Spanish names of the form controls, in screen order, for the error summary. */
export const TENANT_FIELDS: readonly { path: string; id: string; label: string }[] = [
  { path: 'legalName', id: 'tenant-legal-name', label: 'Razón social' },
  { path: 'tradeName', id: 'tenant-trade-name', label: 'Nombre comercial' },
  { path: 'ruc', id: 'tenant-ruc', label: 'RUC' },
  { path: 'clinicType', id: 'tenant-type', label: 'Tipo de clínica' },
  { path: 'countryCode', id: 'tenant-country', label: 'País' },
  { path: 'city', id: 'tenant-city', label: 'Ciudad' },
  { path: 'address', id: 'tenant-address', label: 'Dirección' },
  { path: 'phone', id: 'tenant-phone', label: 'Teléfono' },
  { path: 'contactEmail', id: 'tenant-contact-email', label: 'Correo de contacto' },
  { path: 'mspEstablishmentNumber', id: 'tenant-msp', label: 'Número de establecimiento del MSP' },
  { path: 'planId', id: 'tenant-plan', label: 'Plan' },
  { path: 'billingPeriod', id: 'tenant-period', label: 'Periodicidad' },
  { path: 'owner.fullName', id: 'tenant-owner-name', label: 'Nombre del OWNER' },
  { path: 'owner.email', id: 'tenant-owner-email', label: 'Correo del OWNER' },
];

/**
 * Onboards a clinic (/platform/tenants/new, SUPER_ADMIN): its data, its plan and its OWNER, who
 * gets a one-time invitation by email. Every mandatory field is checked before sending and the
 * summary names each missing one; the api checks them again (FR-007).
 */
@Component({
  selector: 'mk-tenant-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    KdActions,
    KdAlert,
    KdButton,
    KdErrorSummary,
    KdField,
    KdFieldGroup,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './tenant-form.page.html',
  styleUrl: './tenants.scss',
})
export class TenantFormPage {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly listLink = AppRoutes.platformTenants;
  protected readonly limits = TENANT_LIMITS;
  protected readonly clinicTypes = CLINIC_TYPES;
  protected readonly clinicTypeLabels = CLINIC_TYPE_LABELS;
  protected readonly countries = Object.entries(COUNTRY_LABELS);
  protected readonly periods = BILLING_PERIODS;
  protected readonly periodLabels = BILLING_PERIOD_LABELS;
  protected readonly planOptionLabel = planOptionLabel;

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly summary = signal<KdFormError[]>([]);

  protected readonly plans = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.plans : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    legalName: ['', [Validators.required, Validators.maxLength(TENANT_LIMITS.legalName), notBlank]],
    tradeName: ['', [Validators.maxLength(TENANT_LIMITS.tradeName), notBlank]],
    ruc: ['', [Validators.required, Validators.pattern(RUC_PATTERN)]],
    clinicType: ['' as ClinicType | '', Validators.required],
    countryCode: ['EC', Validators.required],
    city: ['', [Validators.required, Validators.maxLength(TENANT_LIMITS.city), notBlank]],
    address: ['', [Validators.required, Validators.maxLength(TENANT_LIMITS.address), notBlank]],
    phone: [
      '',
      [
        Validators.required,
        Validators.minLength(TENANT_LIMITS.phoneMin),
        Validators.maxLength(TENANT_LIMITS.phoneMax),
        notBlank,
      ],
    ],
    contactEmail: [
      '',
      [Validators.required, Validators.email, Validators.maxLength(TENANT_LIMITS.email)],
    ],
    mspEstablishmentNumber: ['', [Validators.maxLength(TENANT_LIMITS.msp), notBlank]],
    planId: ['', Validators.required],
    billingPeriod: ['MONTHLY' as BillingPeriod, Validators.required],
    owner: inject(NonNullableFormBuilder).group({
      fullName: [
        '',
        [Validators.required, Validators.maxLength(TENANT_LIMITS.ownerName), notBlank],
      ],
      email: [
        '',
        [Validators.required, Validators.email, Validators.maxLength(TENANT_LIMITS.email)],
      ],
    }),
  });

  /** Kept across retries of the same data, so a lost response never onboards the clinic twice. */
  private creationKey: PlatformCreationKey | null = null;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.tenants.plans('ACTIVE').subscribe({
      next: (plans) => this.state.set({ kind: 'ready', plans }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected rucError(): string | null {
    const ruc = this.form.controls.ruc;
    return ruc.touched && ruc.hasError('pattern')
      ? 'El RUC debe tener exactamente 13 dígitos.'
      : null;
  }

  protected submit(): void {
    if (this.submitting() || this.state().kind !== 'ready') {
      return;
    }
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.summary.set(formErrors(this.form));
      this.focusSummary();
      return;
    }
    this.summary.set([]);
    this.creationKey ??= newPlatformCreationKey();
    this.submitting.set(true);
    this.tenants.create(tenantRequest(this.form.getRawValue()), this.creationKey).subscribe({
      next: (tenant) => {
        this.submitting.set(false);
        this.notices.show(
          `Se registró ${clinicName(tenant)} y enviamos la invitación a su OWNER (${tenant.owner.maskedEmail}).`,
        );
        void this.router.navigateByUrl(platformTenantUrl(tenant.id));
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        if (!isRetryable(error)) {
          // The API answered: corrected data must go with a new key.
          this.creationKey = null;
        }
        this.showError(error);
      },
    });
  }

  private showError(error: unknown): void {
    const problem = toProblem(error);
    if (problem?.code === 'RUC_ALREADY_REGISTERED') {
      const ruc = this.form.controls.ruc;
      ruc.setErrors({ server: ERROR_MESSAGES.RUC_ALREADY_REGISTERED });
      ruc.markAsTouched();
    } else if (problem?.code === 'PLAN_ARCHIVED') {
      const plan = this.form.controls.planId;
      plan.setErrors({ server: ERROR_MESSAGES.PLAN_ARCHIVED });
      plan.markAsTouched();
    } else if (problem?.code === 'VALIDATION') {
      applyServerErrors(this.form, error);
    }
    this.summary.set(formErrors(this.form));
    this.failure.set(errorMessage(error));
    this.focusSummary();
  }

  private focusSummary(): void {
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        const target =
          host.querySelector<HTMLElement>('kd-error-summary [role="alert"]') ??
          host.querySelector<HTMLElement>('[data-testid="form-error"]');
        target?.focus();
      },
      { injector: this.injector },
    );
  }
}

/** Errors of the form in screen order, each naming its field and linking to it. */
export function formErrors(form: FormGroup): KdFormError[] {
  const errors: KdFormError[] = [];
  for (const field of TENANT_FIELDS) {
    const control: AbstractControl | null = form.get(field.path);
    if (control?.invalid) {
      const message =
        field.path === 'ruc' && control.hasError('pattern')
          ? 'debe tener exactamente 13 dígitos.'
          : (controlErrorMessage(control) ?? 'El valor no es válido.').replace(/^\p{Lu}/u, (c) =>
              c.toLowerCase(),
            );
      errors.push({ fieldId: field.id, message: `${field.label}: ${message}` });
    }
  }
  return errors;
}

/** CreateTenantRequest from the form: trimmed, optional fields left out when empty. */
export function tenantRequest(value: {
  legalName: string;
  tradeName: string;
  ruc: string;
  clinicType: ClinicType | '';
  countryCode: string;
  city: string;
  address: string;
  phone: string;
  contactEmail: string;
  mspEstablishmentNumber: string;
  planId: string;
  billingPeriod: BillingPeriod;
  owner: { fullName: string; email: string };
}): Omit<CreateTenantRequest, 'id'> {
  const tradeName = value.tradeName.trim();
  const msp = value.mspEstablishmentNumber.trim();
  return {
    legalName: value.legalName.trim(),
    tradeName: tradeName || null,
    ruc: value.ruc.trim(),
    clinicType: value.clinicType as ClinicType,
    countryCode: value.countryCode,
    city: value.city.trim(),
    address: value.address.trim(),
    phone: value.phone.trim(),
    contactEmail: value.contactEmail.trim(),
    mspEstablishmentNumber: msp || null,
    planId: value.planId,
    billingPeriod: value.billingPeriod,
    owner: { fullName: value.owner.fullName.trim(), email: value.owner.email.trim() },
  };
}

/** Network failures and server errors may have reached the API: retry with the same key. */
export function isRetryable(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}
