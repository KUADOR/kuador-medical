import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  Injector,
  afterNextRender,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import type { Role } from '../../core/api/api-types';
import { AppRoutes } from '../../core/auth/navigation';
import { SessionStore } from '../../core/auth/session.store';
import { errorMessage } from '../../core/errors/error-messages';
import { NoticeService } from '../../core/notice/notice.service';
import { permissionLabel } from '../../core/roles/permission-labels';
import { RolesService } from '../../core/roles/roles.service';

type LoadState =
  { kind: 'loading' } | { kind: 'loaded'; roles: Role[] } | { kind: 'failed'; message: string };

/** System roles first (OWNER, ADMIN), then the rest by name. */
export function sortRoles(roles: readonly Role[]): Role[] {
  return [...roles].sort(
    (a, b) =>
      Number(b.systemRole) - Number(a.systemRole) ||
      a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }),
  );
}

/** Roles of the clinic: list, plus create/edit/delete according to the permissions of GET /me. */
@Component({
  selector: 'mk-roles-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './roles.page.html',
})
export class RolesPage {
  private readonly rolesService = inject(RolesService);
  private readonly session = inject(SessionStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly newRoleLink = AppRoutes.newRole;
  protected readonly rolesLink = AppRoutes.roles;
  protected readonly permissionLabel = permissionLabel;
  protected readonly canCreate = computed(() => this.session.permissions().has('roles.create'));
  protected readonly canUpdate = computed(() => this.session.permissions().has('roles.update'));
  protected readonly canDelete = computed(() => this.session.permissions().has('roles.delete'));
  protected readonly clinicName = computed(() => this.session.account()?.tenant?.name ?? null);

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly roles = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.roles : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });

  protected readonly notice = signal<string | null>(inject(NoticeService).take());
  protected readonly actionFailure = signal<string | null>(null);
  protected readonly confirming = signal<Role | null>(null);
  protected readonly deleting = signal(false);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.rolesService.list().subscribe({
      next: (roles) => this.state.set({ kind: 'loaded', roles: sortRoles(roles) }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected askDelete(role: Role): void {
    this.notice.set(null);
    this.actionFailure.set(null);
    this.confirming.set(role);
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector<HTMLElement>('[data-testid="confirm-delete"]')
          ?.focus(),
      { injector: this.injector },
    );
  }

  protected cancelDelete(): void {
    const role = this.confirming();
    this.confirming.set(null);
    if (role) {
      afterNextRender(
        () =>
          this.host.nativeElement
            .querySelector<HTMLElement>(`[data-delete-role="${CSS.escape(role.id)}"]`)
            ?.focus(),
        { injector: this.injector },
      );
    }
  }

  protected confirmDelete(): void {
    const role = this.confirming();
    if (!role || this.deleting()) {
      return;
    }
    this.deleting.set(true);
    this.rolesService.delete(role.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.confirming.set(null);
        this.notice.set(`Se eliminó el rol «${role.name}».`);
        this.load();
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.confirming.set(null);
        this.actionFailure.set(errorMessage(error));
      },
    });
  }
}
