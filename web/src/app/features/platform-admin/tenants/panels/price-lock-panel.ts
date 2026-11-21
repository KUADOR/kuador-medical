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
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import type { Subscription } from '../../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../../core/errors/error-messages';
import { applyServerErrors, notBlank } from '../../../../core/forms/form-errors';
import { PlatformTenantsService } from '../../../../core/platform/platform-tenants.service';
import { KdActions, KdAlert, KdButton, KdField, KdInput } from '../../../../design-system';
import { TENANT_LIMITS, formatDay, todayInEcuador } from '../tenant-labels';

/** The lock date (yyyy-MM-dd) cannot be before today in Ecuador. */
export const notBeforeToday: ValidatorFn = (control): ValidationErrors | null => {
  const value: unknown = control.value;
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  return value < todayInEcuador() ? { server: 'La fecha no puede ser anterior a hoy.' } : null;
};

/**
 * Price lock (FR-006): the clinic keeps its contracted price until the chosen day, inclusive;
 * new price versions do not apply to it before then. It can be removed. Audited by the api.
 */
@Component({
  selector: 'mk-price-lock-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, KdActions, KdAlert, KdButton, KdField, KdInput],
  templateUrl: './price-lock-panel.html',
  styleUrl: '../tenants.scss',
})
export class PriceLockPanel {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly tenantId = input.required<string>();
  readonly subscription = input.required<Subscription>();
  readonly changed = output<Subscription>();
  readonly reload = output<void>();

  protected readonly limits = TENANT_LIMITS;
  protected readonly today = todayInEcuador();
  protected readonly lockedUntil = computed(() => formatDay(this.subscription().priceLockedUntil));

  protected readonly form = inject(NonNullableFormBuilder).group({
    lockedUntil: ['', [Validators.required, notBeforeToday]],
    reason: ['', [Validators.maxLength(TENANT_LIMITS.reason), notBlank]],
  });

  protected readonly busy = signal<'lock' | 'unlock' | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly stale = signal(false);

  protected lock(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focus('[aria-invalid="true"]');
      return;
    }
    const { lockedUntil, reason } = this.form.getRawValue();
    this.save('lock', lockedUntil, reason.trim());
  }

  protected unlock(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    this.save('unlock', null, this.form.getRawValue().reason.trim());
  }

  protected reloadData(): void {
    this.clearMessages();
    this.reload.emit();
  }

  private save(kind: 'lock' | 'unlock', lockedUntil: string | null, reason: string): void {
    this.busy.set(kind);
    this.tenants
      .setPriceLock(this.tenantId(), {
        lockedUntil,
        ...(reason ? { reason } : {}),
        version: this.subscription().version,
      })
      .subscribe({
        next: (subscription) => {
          this.busy.set(null);
          this.form.reset({ lockedUntil: '', reason: '' });
          this.notice.set(
            subscription.priceLockedUntil
              ? `El precio contratado queda congelado hasta el ${formatDay(subscription.priceLockedUntil)}.`
              : 'Se quitó el congelamiento: el precio vigente del plan aplica desde la próxima renovación.',
          );
          this.changed.emit(subscription);
        },
        error: (error: unknown) => {
          this.busy.set(null);
          const code = toProblem(error)?.code;
          if (code === 'VALIDATION') {
            applyServerErrors(this.form, error);
          }
          this.stale.set(code === 'VERSION_CONFLICT');
          this.failure.set(errorMessage(error));
          this.focus('[data-testid="price-lock-failure"]');
        },
      });
  }

  private clearMessages(): void {
    this.notice.set(null);
    this.failure.set(null);
    this.stale.set(false);
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
