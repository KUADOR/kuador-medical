import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { PurgeRequest, Subscription, Tenant } from '../../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../../core/errors/error-messages';
import { applyServerErrors, notBlank } from '../../../../core/forms/form-errors';
import {
  PlatformCreationKey,
  PlatformTenantsService,
  newPlatformCreationKey,
} from '../../../../core/platform/platform-tenants.service';
import { KdActions, KdAlert, KdButton, KdField, KdInput } from '../../../../design-system';
import { isRetryable } from '../../../../core/errors/retryable';
import { TENANT_LIMITS, formatDate, formatDateTime, purgeAvailable } from '../tenant-labels';

export const RUC_MISMATCH_MESSAGE = 'El RUC no coincide con el de la clínica.';

/**
 * Definitive purge of a cancelled clinic in two separate, audited steps (plan D6, ADR-018):
 * a request with a reason, then the confirmation typing the clinic RUC. Offered only once the
 * retention ended. Clinical records are not purged until the legal period is validated.
 */
@Component({
  selector: 'mk-purge-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, KdActions, KdAlert, KdButton, KdField, KdInput],
  templateUrl: './purge-panel.html',
  styleUrl: '../tenants.scss',
})
export class PurgePanel {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly tenant = input.required<Tenant>();
  readonly subscription = input.required<Subscription>();
  /** The purge was confirmed: the page reads the clinic again. */
  readonly changed = output<void>();

  protected readonly limits = TENANT_LIMITS;
  protected readonly available = computed(() => purgeAvailable(this.subscription()));
  protected readonly retentionEnd = computed(() => formatDate(this.subscription().retentionEndsAt));

  protected readonly requestForm = this.fb.group({
    reason: ['', [Validators.required, Validators.maxLength(TENANT_LIMITS.reason), notBlank]],
  });
  protected readonly confirmForm = this.fb.group({
    rucConfirmation: ['', Validators.required],
  });

  /** Pending request of step 1, kept to confirm it in step 2. */
  protected readonly request = signal<PurgeRequest | null>(null);
  protected readonly confirmed = signal<PurgeRequest | null>(null);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  private requestKey: PlatformCreationKey | null = null;

  protected formatDateTime = formatDateTime;

  protected requestPurge(): void {
    if (this.busy() || !this.available()) {
      return;
    }
    this.failure.set(null);
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      this.focus('#purge-reason');
      return;
    }
    this.requestKey ??= newPlatformCreationKey();
    this.busy.set(true);
    this.tenants
      .requestPurge(
        this.tenant().id,
        { reason: this.requestForm.getRawValue().reason.trim() },
        this.requestKey,
      )
      .subscribe({
        next: (request) => {
          this.busy.set(false);
          this.requestKey = null;
          this.request.set(request);
          this.focus('#purge-ruc');
        },
        error: (error: unknown) => {
          if (!isRetryable(error)) {
            this.requestKey = null;
          }
          if (toProblem(error)?.code === 'VALIDATION') {
            applyServerErrors(this.requestForm, error);
          }
          this.fail(error);
        },
      });
  }

  protected confirmPurge(): void {
    const request = this.request();
    if (!request || this.busy()) {
      return;
    }
    this.failure.set(null);
    const control = this.confirmForm.controls.rucConfirmation;
    const typed = control.value.trim();
    if (typed && typed !== this.tenant().ruc) {
      control.setErrors({ server: RUC_MISMATCH_MESSAGE });
    }
    if (this.confirmForm.invalid) {
      this.confirmForm.markAllAsTouched();
      this.focus('#purge-ruc');
      return;
    }
    this.busy.set(true);
    this.tenants.confirmPurge(this.tenant().id, request.id, typed).subscribe({
      next: (confirmed) => {
        this.busy.set(false);
        this.request.set(null);
        this.confirmed.set(confirmed);
        this.changed.emit();
      },
      error: (error: unknown) => {
        const problem = toProblem(error);
        const mismatch = problem?.errors?.some(
          (fieldError) => fieldError.field === 'rucConfirmation' && fieldError.code === 'MISMATCH',
        );
        if (mismatch) {
          control.setErrors({ server: RUC_MISMATCH_MESSAGE });
          control.markAsTouched();
        }
        if (problem?.code === 'PURGE_NOT_ALLOWED') {
          this.request.set(null);
        }
        this.fail(error);
      },
    });
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    this.failure.set(errorMessage(error));
    this.focus('[data-testid="purge-failure"]');
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
