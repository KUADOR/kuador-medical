import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { AccountStatus, StaffUser } from '../../core/api/api-types';
import { errorMessage } from '../../core/errors/error-messages';
import { NoticeService } from '../../core/notice/notice.service';
import { AppRoutes } from '../../core/session/navigation';
import { SessionStore } from '../../core/session/session.store';
import { StaffUserQuery, StaffUsersService } from '../../core/staff/staff-users.service';
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
} from '../../design-system';
import { describeAssignments } from './role-assignments/role-assignments';
import {
  ACCOUNT_STATUSES,
  ACCOUNT_STATUS_LABELS,
  ACCOUNT_STATUS_TONES,
  fullName,
} from './staff-labels';

export const STAFF_PAGE_SIZE = 20;

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; items: StaffUser[]; page: number; totalItems: number }
  | { kind: 'failed'; message: string };

/** Staff of the clinic: search, filters and paging; invite according to the permissions of GET /me. */
@Component({
  selector: 'mk-staff-users-page',
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
  templateUrl: './staff-users.page.html',
  styleUrl: './staff-users.page.scss',
})
export class StaffUsersPage {
  private readonly staff = inject(StaffUsersService);
  private readonly session = inject(SessionStore);

  protected readonly newLink = AppRoutes.newStaffUser;
  protected readonly staffLink = AppRoutes.staff;
  protected readonly statuses = ACCOUNT_STATUSES;
  protected readonly statusLabels = ACCOUNT_STATUS_LABELS;
  protected readonly statusTones = ACCOUNT_STATUS_TONES;
  protected readonly fullName = fullName;
  protected readonly describeAssignments = describeAssignments;

  /** Inviting needs the roles to choose from (roles.read) besides identity.users.manage. */
  protected readonly canInvite = computed(
    () =>
      this.session.permissions().has('identity.users.manage') &&
      this.session.permissions().has('roles.read'),
  );
  protected readonly branches = computed(() => this.session.account()?.availableBranches ?? []);
  protected readonly clinicName = computed(() => this.session.account()?.clinic?.name ?? null);

  protected readonly filters = inject(NonNullableFormBuilder).group({
    q: [''],
    status: ['' as AccountStatus | ''],
    branchId: [''],
  });

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  private query: StaffUserQuery = { page: 0, size: STAFF_PAGE_SIZE };

  protected readonly users = computed(() => {
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
    const from = current.page * STAFF_PAGE_SIZE + 1;
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
    const { q, status, branchId } = this.filters.getRawValue();
    this.query = {
      q: q.trim() || undefined,
      status: status || undefined,
      branchId: branchId || undefined,
      page: 0,
      size: STAFF_PAGE_SIZE,
    };
    this.load();
  }

  protected clearFilters(): void {
    this.filters.reset({ q: '', status: '', branchId: '' });
    this.search();
  }

  protected goTo(delta: number): void {
    this.query = { ...this.query, page: Math.max(0, (this.query.page ?? 0) + delta) };
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.staff.list(this.query).subscribe({
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
