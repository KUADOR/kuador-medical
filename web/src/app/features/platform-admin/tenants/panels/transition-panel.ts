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
import type { Subscription, SubscriptionStatus } from '../../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../../core/errors/error-messages';
import { applyServerErrors, notBlank } from '../../../../core/forms/form-errors';
import { PlatformTenantsService } from '../../../../core/platform/platform-tenants.service';
import { KdActions, KdAlert, KdButton, KdField, KdInput } from '../../../../design-system';
import {
  SUBSCRIPTION_STATUS_LABELS,
  TENANT_LIMITS,
  formatDate,
  transitionActionLabel,
  transitionTargets,
} from '../tenant-labels';

/** What a manual transition does to the clinic, in Spanish, for the confirmation. */
export function transitionConsequence(
  from: SubscriptionStatus,
  target: SubscriptionStatus,
): string {
  switch (target) {
    case 'SUSPENDED':
      return 'La clínica quedará en solo lectura y exportación: no podrá crear ni editar registros. Sus datos se conservan y se puede reactivar.';
    case 'CANCELLED':
      return 'La clínica pasará a retención: solo lectura y exportación para el OWNER. Durante la retención solo un SUPER_ADMIN puede reactivarla; al terminar se podrá purgar.';
    case 'ACTIVE':
      return from === 'CANCELLED'
        ? 'La clínica pasará a Activa con un período nuevo desde hoy, al precio vigente de su plan (o al congelado), y se cancelará cualquier solicitud de purga pendiente.'
        : 'La clínica recupera el acceso completo según su plan, con sus datos intactos.';
    default:
      return `La suscripción pasará a ${SUBSCRIPTION_STATUS_LABELS[target]}.`;
  }
}

/** Activation refused because the trial plan has no price: the plan change panel solves it. */
export const ACTIVATION_NEEDS_PAID_PLAN_TRIAL =
  'No se puede activar: el plan actual no tiene tarifa para la periodicidad contratada. Primero cambia la clínica a un plan pagado en «Cambiar el plan» y luego actívala.';

/** Activation refused because the plan has no price, in a state where the plan cannot change. */
export const ACTIVATION_NEEDS_PAID_PLAN =
  'No se puede activar: el plan actual no tiene tarifa para la periodicidad contratada y en este estado no se permite cambiar de plan.';

/**
 * Manual transitions of the subscription (ADR-016): the targets the api allows now, always with
 * a reason for the audit trail and an explicit confirmation. Reactivating a CANCELLED clinic is
 * offered only while the api reports it reactivable (inside the retention, not purged; plan D12).
 */
@Component({
  selector: 'mk-transition-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, KdActions, KdAlert, KdButton, KdField, KdInput],
  templateUrl: './transition-panel.html',
  styleUrl: '../tenants.scss',
})
export class TransitionPanel {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly tenantId = input.required<string>();
  readonly clinic = input.required<string>();
  readonly subscription = input.required<Subscription>();
  /** The api applied the transition and answered the new subscription. */
  readonly changed = output<Subscription>();
  /** The data on screen is stale (version conflict or invalid transition). */
  readonly reload = output<void>();

  protected readonly limits = TENANT_LIMITS;
  protected readonly targets = computed(() => transitionTargets(this.subscription()));
  protected readonly cancelledClosed = computed(() => {
    const sub = this.subscription();
    return sub.status === 'CANCELLED' && !sub.reactivable;
  });
  protected readonly retentionEnd = computed(() => formatDate(this.subscription().retentionEndsAt));

  protected readonly form = inject(NonNullableFormBuilder).group({
    target: ['' as SubscriptionStatus | '', Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(TENANT_LIMITS.reason), notBlank]],
  });

  protected readonly confirming = signal<SubscriptionStatus | null>(null);
  protected readonly busy = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly stale = signal(false);

  protected actionLabel(target: SubscriptionStatus): string {
    return transitionActionLabel(this.subscription().status, target);
  }

  protected consequence(target: SubscriptionStatus): string {
    return transitionConsequence(this.subscription().status, target);
  }

  protected review(): void {
    this.clearMessages();
    const { target } = this.form.getRawValue();
    if (this.form.invalid || !target) {
      this.form.markAllAsTouched();
      this.focus('[aria-invalid="true"]');
      return;
    }
    this.confirming.set(target);
    this.focus('[data-testid="confirm-transition"]');
  }

  protected cancel(): void {
    this.confirming.set(null);
    this.focus('#transition-target');
  }

  protected confirm(): void {
    const target = this.confirming();
    if (!target || this.busy()) {
      return;
    }
    const from = this.subscription().status;
    this.busy.set(true);
    this.tenants
      .transition(this.tenantId(), {
        target,
        reason: this.form.getRawValue().reason.trim(),
        version: this.subscription().version,
      })
      .subscribe({
        next: (subscription) => {
          this.busy.set(false);
          this.confirming.set(null);
          this.form.reset({ target: '', reason: '' });
          this.notice.set(
            `${transitionActionLabel(from, target)}: hecho. La suscripción quedó en ${SUBSCRIPTION_STATUS_LABELS[subscription.status]}.`,
          );
          this.changed.emit(subscription);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.confirming.set(null);
          const code = toProblem(error)?.code;
          if (code === 'VALIDATION') {
            applyServerErrors(this.form, error);
          }
          // The api offered ACTIVE for this very version, so refusing it means the plan has no
          // price for the billing period (e.g. the trial plan), not stale data: reloading won't help.
          if (code === 'INVALID_SUBSCRIPTION_TRANSITION' && target === 'ACTIVE') {
            this.failure.set(
              from === 'TRIAL' ? ACTIVATION_NEEDS_PAID_PLAN_TRIAL : ACTIVATION_NEEDS_PAID_PLAN,
            );
            this.focus('[data-testid="transition-failure"]');
            return;
          }
          this.stale.set(
            code === 'VERSION_CONFLICT' ||
              code === 'INVALID_SUBSCRIPTION_TRANSITION' ||
              code === 'REACTIVATION_WINDOW_CLOSED',
          );
          this.failure.set(errorMessage(error));
          this.focus('[data-testid="transition-failure"]');
        },
      });
  }

  protected reloadData(): void {
    this.clearMessages();
    this.reload.emit();
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
