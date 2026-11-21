import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import type { Subscription, Tenant } from '../../../core/api/api-types';
import { errorMessage, toProblem } from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import { PlatformTenantsService } from '../../../core/platform/platform-tenants.service';
import { AppRoutes } from '../../../core/session/navigation';
import { SessionStore } from '../../../core/session/session.store';
import {
  KdActions,
  KdAlert,
  KdBadge,
  KdButton,
  KdDetails,
  KdLoadingState,
  KdPage,
  KdTable,
} from '../../../design-system';
import { OwnerPanel } from './panels/owner-panel';
import { PlanChangePanel } from './panels/plan-change-panel';
import { PriceLockPanel } from './panels/price-lock-panel';
import { PurgePanel } from './panels/purge-panel';
import { TransitionPanel } from './panels/transition-panel';
import {
  ACCESS_MODE_LABELS,
  BILLING_PERIOD_LABELS,
  CLINIC_TYPE_LABELS,
  COUNTRY_LABELS,
  PLAN_CHANGE_KIND_LABELS,
  SUBSCRIPTION_STATUS_LABELS,
  SUBSCRIPTION_STATUS_TONES,
  TRANSITION_CAUSE_LABELS,
  clinicName,
  formatDate,
  formatDateTime,
  formatDay,
  formatMoney,
} from './tenant-labels';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; tenant: Tenant; subscription: Subscription }
  | { kind: 'failed'; message: string; notFound: boolean };

/** Relevant dates of a subscription, only those it has (term / value). */
export function subscriptionDates(subscription: Subscription): { term: string; value: string }[] {
  const dates: [string, string | null][] = [
    ['Fin de la prueba', formatDate(subscription.trialEndsAt)],
    ['Inicio del período', formatDate(subscription.currentPeriodStart)],
    ['Fin del período', formatDate(subscription.currentPeriodEnd)],
    ['Fin de la gracia', formatDate(subscription.graceEndsAt)],
    ['Suspendida el', formatDate(subscription.suspendedAt)],
    ['Cancelada el', formatDate(subscription.cancelledAt)],
    ['Fin de la retención', formatDate(subscription.retentionEndsAt)],
    ['Reactivada el', formatDate(subscription.reactivatedAt)],
  ];
  return dates
    .filter((entry): entry is [string, string] => entry[1] !== null)
    .map(([term, value]) => ({ term, value }));
}

/**
 * A clinic of the platform (/platform/tenants/:id, SUPER_ADMIN): its data, its OWNER onboarding,
 * its subscription with the history of periods and transitions, and the actions on it (manual
 * transitions and reactivation, plan change, price lock and the two-step purge). Each action is
 * offered only when the api says it is possible; the api checks everything again.
 */
@Component({
  selector: 'mk-tenant-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    KdActions,
    KdAlert,
    KdBadge,
    KdButton,
    KdDetails,
    KdLoadingState,
    KdPage,
    KdTable,
    OwnerPanel,
    PlanChangePanel,
    PriceLockPanel,
    PurgePanel,
    TransitionPanel,
  ],
  templateUrl: './tenant-detail.page.html',
  styleUrl: './tenants.scss',
})
export class TenantDetailPage implements OnInit {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly session = inject(SessionStore);

  /** Route parameter :id. */
  readonly id = input.required<string>();

  protected readonly listLink = AppRoutes.platformTenants;
  protected readonly statusLabels = SUBSCRIPTION_STATUS_LABELS;
  protected readonly statusTones = SUBSCRIPTION_STATUS_TONES;
  protected readonly accessLabels = ACCESS_MODE_LABELS;
  protected readonly periodLabels = BILLING_PERIOD_LABELS;
  protected readonly clinicTypes = CLINIC_TYPE_LABELS;
  protected readonly countries = COUNTRY_LABELS;
  protected readonly causeLabels = TRANSITION_CAUSE_LABELS;
  protected readonly changeKindLabels = PLAN_CHANGE_KIND_LABELS;
  protected readonly formatDate = formatDate;
  protected readonly formatDateTime = formatDateTime;
  protected readonly formatDay = formatDay;
  protected readonly formatMoney = formatMoney;

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  protected readonly canPurge = computed(() =>
    this.session.permissions().has('platform.tenants.purge'),
  );
  protected readonly canManagePlans = computed(() =>
    this.session.permissions().has('platform.plans.manage'),
  );

  protected readonly tenant = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.tenant : null;
  });
  protected readonly subscription = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.subscription : null;
  });
  protected readonly heading = computed(() => {
    const tenant = this.tenant();
    return tenant ? clinicName(tenant) : 'Clínica';
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current : null;
  });
  protected readonly dates = computed(() => {
    const subscription = this.subscription();
    return subscription ? subscriptionDates(subscription) : [];
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    forkJoin({
      tenant: this.tenants.get(this.id()),
      subscription: this.tenants.subscription(this.id()),
    }).subscribe({
      next: ({ tenant, subscription }) => this.state.set({ kind: 'ready', tenant, subscription }),
      error: (error: unknown) =>
        this.state.set({
          kind: 'failed',
          message: errorMessage(error),
          notFound: toProblem(error)?.code === 'NOT_FOUND',
        }),
    });
  }

  /** A panel saved the subscription: show it, and refresh the clinic summary behind it. */
  protected subscriptionChanged(subscription: Subscription): void {
    const tenant = this.tenant();
    if (!tenant) {
      return;
    }
    this.notice.set(null);
    this.state.set({ kind: 'ready', tenant, subscription });
    this.reloadTenant();
  }

  /** A panel changed something the subscription response does not carry: read both again. */
  protected refresh(): void {
    const tenant = this.tenant();
    if (!tenant) {
      return;
    }
    this.notice.set(null);
    forkJoin({
      tenant: this.tenants.get(tenant.id),
      subscription: this.tenants.subscription(tenant.id),
    }).subscribe({
      next: (fresh) => this.state.set({ kind: 'ready', ...fresh }),
      // The action was applied; a failed refresh only leaves the previous data on screen.
      error: () => undefined,
    });
  }

  protected reloadTenant(): void {
    const current = this.state();
    if (current.kind !== 'ready') {
      return;
    }
    this.tenants.get(current.tenant.id).subscribe({
      next: (tenant) => {
        const latest = this.state();
        if (latest.kind === 'ready') {
          this.state.set({ ...latest, tenant });
        }
      },
      error: () => undefined,
    });
  }
}
