import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type {
  BillingPeriod,
  PlanChangePreview,
  PlatformPlan,
  Subscription,
} from '../../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../../core/errors/error-messages';
import {
  PlatformCreationKey,
  PlatformTenantsService,
  newPlatformCreationKey,
} from '../../../../core/platform/platform-tenants.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdDetails,
  KdField,
  KdInput,
  KdLoadingState,
} from '../../../../design-system';
import { isRetryable } from '../tenant-form.page';
import {
  BILLING_PERIODS,
  BILLING_PERIOD_LABELS,
  LIMIT_LABELS,
  MODULE_LABELS,
  PLAN_CHANGE_KIND_LABELS,
  canChangePlan,
  formatDate,
  formatMoney,
  planOptionLabel,
} from '../tenant-labels';

type PlansState =
  | { kind: 'loading' }
  | { kind: 'ready'; plans: PlatformPlan[] }
  | { kind: 'failed'; message: string };

/**
 * Plan change of a clinic (FR-012, FR-013): first a preview that changes nothing (kind, when it
 * applies, prorated amount of an upgrade, and for a downgrade the usage above the target limits
 * and the modules it loses), then the confirmation. A downgrade with excess is never blocked.
 */
@Component({
  selector: 'mk-plan-change-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    KdActions,
    KdAlert,
    KdButton,
    KdDetails,
    KdField,
    KdInput,
    KdLoadingState,
  ],
  templateUrl: './plan-change-panel.html',
  styleUrl: '../tenants.scss',
})
export class PlanChangePanel implements OnInit {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  readonly tenantId = input.required<string>();
  readonly subscription = input.required<Subscription>();
  /** The api applied or scheduled the change. */
  readonly changed = output<void>();
  /** The data on screen is stale (version conflict or status change). */
  readonly reload = output<void>();

  protected readonly periods = BILLING_PERIODS;
  protected readonly periodLabels = BILLING_PERIOD_LABELS;
  protected readonly kindLabels = PLAN_CHANGE_KIND_LABELS;
  protected readonly limitLabels = LIMIT_LABELS;
  protected readonly moduleLabels = MODULE_LABELS;
  protected readonly planOptionLabel = planOptionLabel;
  protected readonly formatDate = formatDate;
  protected readonly formatMoney = formatMoney;

  protected readonly allowed = computed(() => canChangePlan(this.subscription()));
  protected readonly plansState = signal<PlansState>({ kind: 'loading' });
  /** Paid plans only: a clinic never moves back to the trial plan. */
  protected readonly plans = computed(() => {
    const state = this.plansState();
    return state.kind === 'ready' ? state.plans.filter((plan) => !plan.trial) : [];
  });
  protected readonly plansFailure = computed(() => {
    const state = this.plansState();
    return state.kind === 'failed' ? state.message : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    targetPlanId: ['', Validators.required],
    billingPeriod: ['MONTHLY' as BillingPeriod, Validators.required],
  });

  protected readonly preview = signal<PlanChangePreview | null>(null);
  protected readonly busy = signal<'preview' | 'apply' | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly stale = signal(false);

  private changeKey: PlatformCreationKey | null = null;

  constructor() {
    // A preview is valid only for the plan and period it was computed for.
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.preview.set(null);
      this.changeKey = null;
    });
  }

  ngOnInit(): void {
    this.form.controls.billingPeriod.setValue(this.subscription().billingPeriod, {
      emitEvent: false,
    });
    if (this.allowed()) {
      this.loadPlans();
    }
  }

  protected loadPlans(): void {
    this.plansState.set({ kind: 'loading' });
    this.tenants.plans('ACTIVE').subscribe({
      next: (plans) => this.plansState.set({ kind: 'ready', plans }),
      error: (error: unknown) =>
        this.plansState.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected showPreview(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focus('#plan-change-target');
      return;
    }
    this.busy.set('preview');
    this.tenants.previewPlanChange(this.tenantId(), this.form.getRawValue()).subscribe({
      next: (preview) => {
        this.busy.set(null);
        this.preview.set(preview);
        this.focus('[data-testid="plan-preview"]');
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  protected discard(): void {
    this.preview.set(null);
    this.changeKey = null;
    this.focus('#plan-change-target');
  }

  protected apply(): void {
    const preview = this.preview();
    if (!preview || this.busy()) {
      return;
    }
    this.clearMessages();
    this.changeKey ??= newPlatformCreationKey();
    this.busy.set('apply');
    this.tenants
      .changePlan(
        this.tenantId(),
        { ...this.form.getRawValue(), version: this.subscription().version },
        this.changeKey,
      )
      .subscribe({
        next: (change) => {
          this.busy.set(null);
          this.changeKey = null;
          this.preview.set(null);
          this.form.reset(
            { targetPlanId: '', billingPeriod: change.billingPeriod },
            { emitEvent: false },
          );
          const prorated = formatMoney(change.proratedAmount, change.currency);
          this.notice.set(
            change.status === 'APPLIED'
              ? `Se aplicó el cambio a ${change.toPlan.name}.${prorated ? ` Prorrateo por los días restantes: ${prorated} sin IVA.` : ''}`
              : `Se programó el cambio a ${change.toPlan.name} para el ${formatDate(change.effectiveAt)}.`,
          );
          this.changed.emit();
        },
        error: (error: unknown) => {
          if (!isRetryable(error)) {
            this.changeKey = null;
          }
          this.fail(error);
        },
      });
  }

  protected reloadData(): void {
    this.clearMessages();
    this.preview.set(null);
    this.reload.emit();
  }

  private fail(error: unknown): void {
    this.busy.set(null);
    const code = toProblem(error)?.code;
    this.stale.set(code === 'VERSION_CONFLICT' || code === 'PLAN_CHANGE_NOT_ALLOWED');
    this.failure.set(errorMessage(error));
    this.focus('[data-testid="plan-change-failure"]');
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
