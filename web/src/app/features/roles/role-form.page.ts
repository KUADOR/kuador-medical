import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
  Injector,
  afterNextRender,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import type { PermissionCode, PermissionInfo, Role } from '../../core/api/api-types';
import { AppRoutes, isOwner } from '../../core/session/navigation';
import { SessionStore } from '../../core/session/session.store';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../core/errors/error-messages';
import {
  applyServerErrors,
  controlErrorMessage,
  focusFirstInvalid,
  notBlank,
} from '../../core/forms/form-errors';
import { NoticeService } from '../../core/notice/notice.service';
import { groupByModule, permissionLabel } from '../../core/roles/permission-labels';
import { RolesService, isOwnerRole } from '../../core/roles/roles.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdFieldGroup,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../design-system';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; catalog: PermissionInfo[]; role: Role | null }
  | { kind: 'failed'; message: string; notFound: boolean };

export const ROLE_NAME_MAX_LENGTH = 80;
export const ROLE_DESCRIPTION_MAX_LENGTH = 500;

/** Why a role opens read-only, or null when it can be edited. */
export type ReadOnlyReason = 'owner' | 'admin' | 'permission';

/**
 * Create (/roles/new), edit or view (/roles/:id) a role of the clinic over the fixed catalog.
 * OWNER is always read-only; ADMIN is editable only by an OWNER; accounts without roles.update
 * only get a read-only view. Permissions the person does not hold cannot be added (anti-escalation,
 * enforced again by the API).
 */
@Component({
  selector: 'mk-role-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdFieldGroup,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './role-form.page.html',
  styleUrl: './role-form.page.scss',
})
export class RoleFormPage implements OnInit {
  private readonly rolesService = inject(RolesService);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Route parameter :id, absent on /roles/new. */
  readonly id = input<string | undefined>();

  protected readonly rolesLink = AppRoutes.roles;
  protected readonly nameMaxLength = ROLE_NAME_MAX_LENGTH;
  protected readonly descriptionMaxLength = ROLE_DESCRIPTION_MAX_LENGTH;
  protected readonly permissionLabel = permissionLabel;

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly versionConflict = signal(false);

  protected readonly isNew = computed(() => !this.id());
  protected readonly role = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.role : null;
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current : null;
  });
  protected readonly readOnlyReason = computed<ReadOnlyReason | null>(() => {
    const role = this.role();
    if (!role) {
      return null;
    }
    if (isOwnerRole(role)) {
      return 'owner';
    }
    if (role.systemKey === 'ADMIN' && !isOwner(this.session.account())) {
      return 'admin';
    }
    return this.session.permissions().has('roles.update') ? null : 'permission';
  });
  protected readonly readOnly = computed(() => this.readOnlyReason() !== null);
  protected readonly heading = computed(() => {
    const role = this.role();
    if (this.isNew()) {
      return 'Nuevo rol';
    }
    if (!role) {
      return 'Rol';
    }
    return this.readOnly() ? `Rol ${role.name}` : `Editar rol ${role.name}`;
  });
  /** Catalog of GET /permissions plus any code the role has that the catalog does not list. */
  protected readonly options = computed<PermissionInfo[]>(() => {
    const current = this.state();
    if (current.kind !== 'ready') {
      return [];
    }
    const known = new Set(current.catalog.map((permission) => permission.code));
    const extra = (current.role?.permissions ?? [])
      .filter((code) => !known.has(code))
      .map((code): PermissionInfo => ({
        code,
        module: 'other',
        scope: 'TENANT',
        description: code,
      }));
    return [...current.catalog, ...extra];
  });
  protected readonly groups = computed(() => groupByModule(this.options()));
  /**
   * Codes the person cannot add: those they do not hold, unless the role already had them
   * (removing a permission is always allowed).
   */
  protected readonly locked = computed<ReadonlySet<PermissionCode>>(() => {
    const held = this.session.permissions();
    const initial = new Set(this.role()?.permissions ?? []);
    return new Set(
      this.options()
        .map((option) => option.code)
        .filter((code) => !held.has(code) && !initial.has(code)),
    );
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(ROLE_NAME_MAX_LENGTH), notBlank]],
    description: ['', [Validators.maxLength(ROLE_DESCRIPTION_MAX_LENGTH)]],
    clinical: [false],
    permissions: [[] as PermissionCode[]],
  });
  protected readonly selected = signal<ReadonlySet<PermissionCode>>(new Set());

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.failure.set(null);
    this.versionConflict.set(false);
    const id = this.id();
    forkJoin({
      catalog: this.rolesService.assignablePermissions(),
      role: id ? this.rolesService.get(id) : of(null),
    }).subscribe({
      next: ({ catalog, role }) => {
        this.fill(role);
        this.state.set({ kind: 'ready', catalog, role });
        if (this.readOnly()) {
          this.form.disable();
        } else {
          this.form.enable();
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

  protected error(name: 'name' | 'description' | 'clinical' | 'permissions'): string | null {
    const control = this.form.controls[name];
    return control.touched ? controlErrorMessage(control) : null;
  }

  protected toggle(code: PermissionCode, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const next = new Set(this.selected());
    if (checked) {
      next.add(code);
    } else {
      next.delete(code);
    }
    this.selected.set(next);
    const control = this.form.controls.permissions;
    control.setValue(
      this.options()
        .map((o) => o.code)
        .filter((c) => next.has(c)),
    );
    control.markAsTouched();
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || this.readOnly() || this.state().kind !== 'ready') {
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

    const { name, description, clinical, permissions } = this.form.getRawValue();
    const body = {
      name: name.trim(),
      description: description.trim() || null,
      clinical,
      permissions,
    };
    const role = this.role();
    const request = role
      ? this.rolesService.update(role.id, { ...body, version: role.version })
      : this.rolesService.create(body);

    this.submitting.set(true);
    request.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.notices.show(
          role
            ? `Se guardaron los cambios del rol «${saved.name}».`
            : `Se creó el rol «${saved.name}».`,
        );
        void this.router.navigateByUrl(AppRoutes.roles);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showError(error);
      },
    });
  }

  private fill(role: Role | null): void {
    const permissions = role?.permissions ?? [];
    this.form.reset({
      name: role?.name ?? '',
      description: role?.description ?? '',
      clinical: role?.clinical ?? false,
      permissions,
    });
    this.selected.set(new Set(permissions));
  }

  private showError(error: unknown): void {
    const code = toProblem(error)?.code;
    if (code === 'ROLE_NAME_TAKEN') {
      const name = this.form.controls.name;
      name.setErrors({ server: ERROR_MESSAGES.ROLE_NAME_TAKEN });
      name.markAsTouched();
    } else if (code === 'VALIDATION') {
      applyServerErrors(this.form, error);
    } else if (code === 'VERSION_CONFLICT') {
      this.versionConflict.set(true);
    }
    this.failure.set(errorMessage(error));
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        (
          host.querySelector<HTMLElement>('[aria-invalid="true"]') ??
          host.querySelector<HTMLElement>('[data-testid="form-error"]')
        )?.focus();
      },
      { injector: this.injector },
    );
  }
}
