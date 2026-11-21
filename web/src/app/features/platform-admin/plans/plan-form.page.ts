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
import {
  FormControl,
  FormGroup,
  FormRecord,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import type {
  BillingPeriod,
  LimitCode,
  LimitInfo,
  ModuleCode,
  ModuleInfo,
  Plan,
  PlanLimits,
  PlanPrice,
  PriceInput,
} from '../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../core/errors/error-messages';
import {
  applyServerErrors,
  focusFirstInvalid,
  integer,
  notBlank,
} from '../../../core/forms/form-errors';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  AMOUNT_PATTERN,
  BILLING_PERIODS,
  BILLING_PERIOD_LABELS,
  annualSavingMonths,
  currentPrice,
  effectiveFromInstant,
  formatDay,
  formatLimit,
  formatMoney,
  platformToday,
  toMoney,
} from '../../../core/platform/plan-labels';
import { PlansService } from '../../../core/platform/plans.service';
import { AppRoutes } from '../../../core/session/navigation';
import { CreationKey, newCreationKey } from '../../../core/staff/staff-users.service';
import {
  KdActions,
  KdAlert,
  KdBadge,
  KdButton,
  KdField,
  KdFieldGroup,
  KdInput,
  KdLoadingState,
  KdPage,
  KdTable,
} from '../../../design-system';

type LoadState =
  | { kind: 'loading' }
  | {
      kind: 'ready';
      modules: ModuleInfo[];
      limits: LimitInfo[];
      plan: Plan | null;
      prices: PlanPrice[];
    }
  | { kind: 'failed'; message: string; notFound: boolean };

type LimitGroup = FormGroup<{
  unlimited: FormControl<boolean>;
  value: FormControl<number | null>;
}>;

export const PLAN_NAME_MAX_LENGTH = 80;
export const PLAN_DESCRIPTION_MAX_LENGTH = 500;
export const PLAN_CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,39}$/;

const planCode: ValidatorFn = (control): ValidationErrors | null =>
  control.value === '' || PLAN_CODE_PATTERN.test(String(control.value)) ? null : { planCode: true };

const amount: ValidatorFn = (control): ValidationErrors | null =>
  control.value === '' || AMOUNT_PATTERN.test(String(control.value).trim())
    ? null
    : { amount: true };

const notBeforeToday: ValidatorFn = (control): ValidationErrors | null =>
  typeof control.value === 'string' && control.value !== '' && control.value < platformToday()
    ? { pastDate: true }
    : null;

/** Row of the price history with what it means today. */
export interface PriceRow {
  price: PlanPrice;
  state: 'current' | 'scheduled' | 'previous';
}

/** Price versions, newest first, marked as current (in force today), scheduled or previous. */
export function priceRows(prices: readonly PlanPrice[], current: readonly PlanPrice[]): PriceRow[] {
  const currentIds = new Set(current.map((price) => price.id));
  const now = Date.now();
  return [...prices]
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))
    .map((price) => ({
      price,
      state: currentIds.has(price.id)
        ? 'current'
        : Date.parse(price.effectiveFrom) > now
          ? 'scheduled'
          : 'previous',
    }));
}

/**
 * Create (/platform/plans/new), edit or view (/platform/plans/:id) a plan of Kuador: modules of
 * the fixed catalog, limits (empty means unlimited) and prices in USD without VAT. New prices
 * are immutable versions published from the edit page; current subscriptions keep their
 * contracted price until their renewal (MEDK-49, CA 1). Archived plans open read-only.
 */
@Component({
  selector: 'mk-plan-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    KdActions,
    KdAlert,
    KdBadge,
    KdButton,
    KdField,
    KdFieldGroup,
    KdInput,
    KdLoadingState,
    KdPage,
    KdTable,
  ],
  templateUrl: './plan-form.page.html',
  styleUrl: './plan-form.page.scss',
})
export class PlanFormPage implements OnInit {
  private readonly plansService = inject(PlansService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Route parameter :id, absent on /platform/plans/new. */
  readonly id = input<string | undefined>();

  protected readonly plansLink = AppRoutes.plans;
  protected readonly nameMaxLength = PLAN_NAME_MAX_LENGTH;
  protected readonly descriptionMaxLength = PLAN_DESCRIPTION_MAX_LENGTH;
  protected readonly periods = BILLING_PERIODS;
  protected readonly periodLabels = BILLING_PERIOD_LABELS;
  protected readonly formatMoney = formatMoney;
  protected readonly formatDay = formatDay;
  protected readonly formatLimit = formatLimit;
  protected readonly today = platformToday();

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly versionConflict = signal(false);
  protected readonly selected = signal<ReadonlySet<ModuleCode>>(new Set());
  protected readonly trial = signal(false);
  protected readonly priceSaving = signal<number | null>(null);

  protected readonly publishing = signal(false);
  protected readonly priceNotice = signal<string | null>(null);
  protected readonly priceFailure = signal<string | null>(null);
  private priceKey: CreationKey = newCreationKey();
  private createKey: CreationKey = newCreationKey();

  protected readonly isNew = computed(() => !this.id());
  protected readonly ready = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current : null;
  });
  protected readonly plan = computed(() => this.ready()?.plan ?? null);
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current : null;
  });
  protected readonly archived = computed(() => this.plan()?.status === 'ARCHIVED');
  protected readonly heading = computed(() => {
    const plan = this.plan();
    if (this.isNew()) {
      return 'Nuevo plan';
    }
    if (!plan) {
      return 'Plan';
    }
    return this.archived() ? `Plan ${plan.name}` : `Editar plan ${plan.name}`;
  });
  /** Module catalog plus any module the plan has that the catalog does not list. */
  protected readonly moduleOptions = computed<ModuleInfo[]>(() => {
    const ready = this.ready();
    if (!ready) {
      return [];
    }
    const known = new Set(ready.modules.map((module) => module.code));
    const extra = (ready.plan?.modules ?? [])
      .filter((code) => !known.has(code))
      .map((code): ModuleInfo => ({ code, label: code }));
    return [...ready.modules, ...extra];
  });
  protected readonly priceHistory = computed(() => {
    const ready = this.ready();
    return ready?.plan ? priceRows(ready.prices, ready.plan.currentPrices) : [];
  });
  /** Prices are set on creation for paid plans, and published as versions afterwards. */
  protected readonly showInitialPrices = computed(() => this.isNew() && !this.trial());
  protected readonly showPriceVersions = computed(() => {
    const plan = this.plan();
    return !!plan && !plan.trial;
  });

  /** One group per limit of the catalog (unlimited flag and value), keyed by its code. */
  private readonly limitControls = new FormRecord<LimitGroup>({});

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, planCode]],
    name: ['', [Validators.required, Validators.maxLength(PLAN_NAME_MAX_LENGTH), notBlank]],
    description: ['', [Validators.maxLength(PLAN_DESCRIPTION_MAX_LENGTH)]],
    trial: [false],
    modules: [[] as ModuleCode[]],
    limits: this.limitControls,
    prices: this.fb.group({
      MONTHLY: ['', [Validators.required, amount]],
      ANNUAL: ['', [Validators.required, amount]],
    }),
  });

  protected readonly priceForm = this.fb.group({
    billingPeriod: ['MONTHLY' as BillingPeriod],
    amount: ['', [Validators.required, amount]],
    effectiveFrom: [platformToday(), [Validators.required, notBeforeToday]],
  });

  ngOnInit(): void {
    this.form.controls.trial.valueChanges.subscribe((trial) => this.onTrialChange(trial));
    this.form.controls.prices.valueChanges.subscribe(({ MONTHLY = '', ANNUAL = '' }) =>
      this.priceSaving.set(annualSavingMonths(toMoney(MONTHLY), toMoney(ANNUAL))),
    );
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.failure.set(null);
    this.versionConflict.set(false);
    const id = this.id();
    forkJoin({
      modules: this.plansService.modules(),
      limits: this.plansService.limits(),
      plan: id ? this.plansService.get(id) : of(null),
      prices: id ? this.plansService.prices(id) : of([] as PlanPrice[]),
    }).subscribe({
      next: (loaded) => {
        this.fill(loaded.plan, loaded.limits);
        this.state.set({ kind: 'ready', ...loaded });
        if (loaded.plan?.status === 'ARCHIVED') {
          this.form.disable();
        }
      },
      error: (error: unknown) =>
        this.state.set({
          kind: 'failed',
          message: errorMessage(error),
          notFound: toProblem(error)?.code === 'NOT_FOUND',
        }),
    });
  }

  protected limitGroup(code: LimitCode): LimitGroup | null {
    return (this.limitControls.controls[code] as LimitGroup | undefined) ?? null;
  }

  protected toggleModule(code: ModuleCode, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const next = new Set(this.selected());
    if (checked) {
      next.add(code);
    } else {
      next.delete(code);
    }
    this.selected.set(next);
    this.form.controls.modules.setValue(
      this.moduleOptions()
        .map((option) => option.code)
        .filter((option) => next.has(option)),
    );
  }

  protected toggleUnlimited(code: LimitCode): void {
    const group = this.limitGroup(code);
    if (!group) {
      return;
    }
    if (group.controls.unlimited.value) {
      group.controls.value.disable();
    } else {
      group.controls.value.enable();
    }
  }

  protected currentAmount(period: BillingPeriod): string {
    const price = currentPrice(this.plan()?.currentPrices ?? [], period);
    return price ? formatMoney(price.amount, price.currency) : 'Sin tarifa';
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || this.archived() || !this.ready()) {
      return;
    }
    this.failure.set(null);
    this.versionConflict.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }

    const value = this.form.getRawValue();
    const common = {
      name: value.name.trim(),
      description: value.description.trim() || null,
      modules: value.modules,
      limits: this.limitsValue(),
    };
    const plan = this.plan();
    const request = plan
      ? this.plansService.update(plan.id, { ...common, version: plan.version })
      : this.plansService.create(
          {
            ...common,
            code: value.code.trim(),
            trial: value.trial,
            prices: value.trial ? [] : this.initialPrices(),
          },
          this.createKey,
        );

    this.submitting.set(true);
    request.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.createKey = newCreationKey();
        this.notices.show(
          plan
            ? `Se guardaron los cambios del plan «${saved.name}».`
            : `Se creó el plan «${saved.name}».`,
        );
        void this.router.navigateByUrl(AppRoutes.plans);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showError(error);
      },
    });
  }

  protected publishPrice(): void {
    const plan = this.plan();
    if (!plan || this.publishing() || this.archived()) {
      return;
    }
    this.priceNotice.set(null);
    this.priceFailure.set(null);
    if (this.priceForm.invalid) {
      this.priceForm.markAllAsTouched();
      afterNextRender(
        () =>
          this.host.nativeElement
            .querySelector<HTMLElement>('.price-form [aria-invalid="true"]')
            ?.focus(),
        { injector: this.injector },
      );
      return;
    }
    const value = this.priceForm.getRawValue();
    const money = toMoney(value.amount) ?? value.amount;
    this.publishing.set(true);
    this.plansService
      .createPrice(
        plan.id,
        {
          billingPeriod: value.billingPeriod,
          currency: 'USD',
          amount: money,
          effectiveFrom: effectiveFromInstant(value.effectiveFrom),
        },
        this.priceKey,
      )
      .subscribe({
        next: (price) => {
          this.publishing.set(false);
          this.priceKey = newCreationKey();
          this.priceForm.reset({
            billingPeriod: value.billingPeriod,
            amount: '',
            effectiveFrom: platformToday(),
          });
          this.priceNotice.set(
            `Se publicó la tarifa ${BILLING_PERIOD_LABELS[price.billingPeriod].toLowerCase()} de ${formatMoney(price.amount, price.currency)}, vigente desde el ${formatDay(price.effectiveFrom)}.`,
          );
          this.reloadPrices(plan.id);
        },
        error: (error: unknown) => {
          this.publishing.set(false);
          if (toProblem(error)?.code === 'VALIDATION') {
            applyServerErrors(this.priceForm, error);
          }
          this.priceFailure.set(errorMessage(error));
        },
      });
  }

  private reloadPrices(id: string): void {
    forkJoin({ plan: this.plansService.get(id), prices: this.plansService.prices(id) }).subscribe({
      next: ({ plan, prices }) => {
        const ready = this.ready();
        if (ready) {
          this.state.set({ ...ready, plan: { ...plan }, prices });
        }
      },
      error: (error: unknown) => this.priceFailure.set(errorMessage(error)),
    });
  }

  private onTrialChange(trial: boolean): void {
    this.trial.set(trial);
    const prices = this.form.controls.prices;
    if (trial || !this.isNew()) {
      prices.disable({ emitEvent: false });
    } else {
      prices.enable({ emitEvent: false });
    }
  }

  private fill(plan: Plan | null, limits: readonly LimitInfo[]): void {
    const group = this.limitControls;
    for (const name of Object.keys(group.controls)) {
      group.removeControl(name);
    }
    const values: PlanLimits = plan?.limits ?? {};
    for (const limit of limits) {
      const value = values[limit.code];
      const unlimited = plan ? value === null || value === undefined : false;
      const limitGroup: LimitGroup = this.fb.group({
        unlimited: [unlimited],
        value: this.fb.control<number | null>({ value: value ?? null, disabled: unlimited }, [
          Validators.required,
          integer,
          Validators.min(0),
        ]),
      });
      group.addControl(limit.code, limitGroup);
    }

    const modules = plan?.modules ?? [];
    this.form.reset({
      code: plan?.code ?? '',
      name: plan?.name ?? '',
      description: plan?.description ?? '',
      trial: plan?.trial ?? false,
      modules,
      prices: { MONTHLY: '', ANNUAL: '' },
    });
    this.selected.set(new Set(modules));
    this.trial.set(plan?.trial ?? false);
    if (plan) {
      // The code and the trial flag are fixed once the plan exists.
      this.form.controls.code.disable();
      this.form.controls.trial.disable();
      this.form.controls.prices.disable();
    }
  }

  private limitsValue(): PlanLimits {
    const limits: Record<string, number | null> = {};
    for (const [code, control] of Object.entries(this.limitControls.controls)) {
      const { unlimited, value } = control.getRawValue();
      limits[code] = unlimited || value === null ? null : Number(value);
    }
    return limits as PlanLimits;
  }

  private initialPrices(): PriceInput[] {
    const prices = this.form.controls.prices.getRawValue();
    return BILLING_PERIODS.map((period) => ({
      billingPeriod: period,
      currency: 'USD',
      amount: toMoney(prices[period]) ?? prices[period],
    }));
  }

  private showError(error: unknown): void {
    const code = toProblem(error)?.code;
    if (code === 'VALIDATION') {
      applyServerErrors(this.form, error);
    } else if (code === 'VERSION_CONFLICT') {
      this.versionConflict.set(true);
    }
    this.failure.set(errorMessage(error));
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        (
          host.querySelector<HTMLElement>('.plan-form [aria-invalid="true"]') ??
          host.querySelector<HTMLElement>('[data-testid="form-error"]')
        )?.focus();
      },
      { injector: this.injector },
    );
  }
}
