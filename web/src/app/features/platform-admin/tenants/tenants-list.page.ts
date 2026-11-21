import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { SubscriptionStatus, TenantListItem } from '../../../core/api/api-types';
import { errorMessage } from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  PlatformTenantsService,
  TenantQuery,
} from '../../../core/platform/platform-tenants.service';
import { AppRoutes, platformTenantUrl } from '../../../core/session/navigation';
import { SessionStore } from '../../../core/session/session.store';
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
import {
  CLINIC_TYPE_LABELS,
  SUBSCRIPTION_STATUSES,
  SUBSCRIPTION_STATUS_LABELS,
  SUBSCRIPTION_STATUS_TONES,
  formatDate,
} from './tenant-labels';

export const TENANTS_PAGE_SIZE = 20;

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; items: TenantListItem[]; page: number; totalItems: number }
  | { kind: 'failed'; message: string };

/**
 * Clinics of the platform (/platform/tenants, SUPER_ADMIN): search by name or RUC, filter by
 * subscription status and paging; each row opens the clinic and its subscription.
 */
@Component({
  selector: 'mk-tenants-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
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
  templateUrl: './tenants-list.page.html',
  styleUrl: './tenants.scss',
})
export class TenantsListPage {
  private readonly tenants = inject(PlatformTenantsService);
  private readonly session = inject(SessionStore);

  protected readonly newLink = AppRoutes.newPlatformTenant;
  protected readonly tenantUrl = platformTenantUrl;
  protected readonly statuses = SUBSCRIPTION_STATUSES;
  protected readonly statusLabels = SUBSCRIPTION_STATUS_LABELS;
  protected readonly statusTones = SUBSCRIPTION_STATUS_TONES;
  protected readonly clinicTypes = CLINIC_TYPE_LABELS;
  protected readonly formatDate = formatDate;

  /** Onboarding needs the plans to choose from (platform.plans.manage). */
  protected readonly canCreate = computed(
    () =>
      this.session.permissions().has('platform.tenants.manage') &&
      this.session.permissions().has('platform.plans.manage'),
  );

  protected readonly filters = inject(NonNullableFormBuilder).group({
    q: [''],
    status: ['' as SubscriptionStatus | ''],
  });

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  private query: TenantQuery = { page: 0, size: TENANTS_PAGE_SIZE };

  protected readonly items = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.items : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly range = computed(() => {
    const current = this.state();
    if (current.kind !== 'loaded' || current.totalItems === 0) {
      return null;
    }
    const from = current.page * TENANTS_PAGE_SIZE + 1;
    const to = Math.min(from + current.items.length - 1, current.totalItems);
    return {
      text: `Mostrando ${from}–${to} de ${current.totalItems}`,
      hasPrevious: current.page > 0,
      hasNext: to < current.totalItems,
    };
  });

  constructor() {
    this.load();
  }

  protected search(): void {
    const { q, status } = this.filters.getRawValue();
    this.query = {
      q: q.trim() || undefined,
      status: status || undefined,
      page: 0,
      size: TENANTS_PAGE_SIZE,
    };
    this.load();
  }

  protected clearFilters(): void {
    this.filters.reset({ q: '', status: '' });
    this.search();
  }

  protected goTo(delta: number): void {
    this.query = { ...this.query, page: Math.max(0, (this.query.page ?? 0) + delta) };
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.tenants.list(this.query).subscribe({
      next: (page) =>
        this.state.set({
          kind: 'loaded',
          items: page.items,
          page: page.page,
          totalItems: page.totalItems,
        }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }
}
