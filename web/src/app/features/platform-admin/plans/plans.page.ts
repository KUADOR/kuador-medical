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
import { RouterLink } from '@angular/router';
import type { BillingPeriod, Plan, PlanStatus } from '../../../core/api/api-types';
import { errorMessage } from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  BILLING_PERIODS,
  BILLING_PERIOD_LABELS,
  PLAN_STATUS_LABELS,
  currentPrice,
  formatMoney,
} from '../../../core/platform/plan-labels';
import { PlansService } from '../../../core/platform/plans.service';
import { AppRoutes, planUrl } from '../../../core/session/navigation';
import {
  KdActions,
  KdAlert,
  KdBadge,
  KdButton,
  KdEmptyState,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
  KdTable,
} from '../../../design-system';

type LoadState =
  { kind: 'loading' } | { kind: 'loaded'; plans: Plan[] } | { kind: 'failed'; message: string };

/** Filter of the list: a plan status, or every plan. */
export type PlanFilter = PlanStatus | 'ALL';

/** Trial plan first, then active before archived, then by name. */
export function sortPlans(plans: readonly Plan[]): Plan[] {
  return [...plans].sort(
    (a, b) =>
      Number(b.trial) - Number(a.trial) ||
      Number(a.status === 'ARCHIVED') - Number(b.status === 'ARCHIVED') ||
      a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }),
  );
}

/**
 * Plans of Kuador (SUPER_ADMIN, platform.plans.manage): list with their current monthly and
 * annual prices (USD, without VAT), and archive. Plans are never deleted (MEDK-49, CA 1).
 */
@Component({
  selector: 'mk-plans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    KdActions,
    KdAlert,
    KdBadge,
    KdButton,
    KdEmptyState,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
    KdTable,
  ],
  templateUrl: './plans.page.html',
  styleUrl: './plans.page.scss',
})
export class PlansPage {
  private readonly plansService = inject(PlansService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly newPlanLink = AppRoutes.newPlan;
  protected readonly planUrl = planUrl;
  protected readonly periods = BILLING_PERIODS;
  protected readonly periodLabels = BILLING_PERIOD_LABELS;
  protected readonly statusLabels = PLAN_STATUS_LABELS;

  protected readonly filter = signal<PlanFilter>('ALL');
  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly plans = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.plans : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });

  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  protected readonly actionFailure = signal<string | null>(null);
  protected readonly confirming = signal<Plan | null>(null);
  protected readonly archiving = signal(false);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    const filter = this.filter();
    this.plansService.list(filter === 'ALL' ? undefined : filter).subscribe({
      next: (plans) => this.state.set({ kind: 'loaded', plans: sortPlans(plans) }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected changeFilter(event: Event): void {
    this.filter.set((event.target as HTMLSelectElement).value as PlanFilter);
    this.confirming.set(null);
    this.load();
  }

  protected price(plan: Plan, period: BillingPeriod): string {
    const price = currentPrice(plan.currentPrices, period);
    return price ? formatMoney(price.amount, price.currency) : plan.trial ? 'Gratis' : 'Sin tarifa';
  }

  /** The trial plan cannot be archived and archived plans stay archived. */
  protected canArchive(plan: Plan): boolean {
    return !plan.trial && plan.status === 'ACTIVE';
  }

  protected askArchive(plan: Plan): void {
    this.notice.set(null);
    this.actionFailure.set(null);
    this.confirming.set(plan);
    this.focusLater('[data-testid="confirm-archive"]');
  }

  protected cancelArchive(): void {
    const plan = this.confirming();
    this.confirming.set(null);
    if (plan) {
      this.focusLater(`[data-archive-plan="${CSS.escape(plan.id)}"]`);
    }
  }

  protected confirmArchive(): void {
    const plan = this.confirming();
    if (!plan || this.archiving()) {
      return;
    }
    this.archiving.set(true);
    this.plansService.archive(plan.id).subscribe({
      next: (archived) => {
        this.archiving.set(false);
        this.confirming.set(null);
        this.notice.set(`Se archivó el plan «${archived.name}».`);
        this.load();
      },
      error: (error: unknown) => {
        this.archiving.set(false);
        this.confirming.set(null);
        this.actionFailure.set(errorMessage(error));
      },
    });
  }

  private focusLater(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
