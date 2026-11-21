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
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import type { Role, StaffUser, UpdateStaffUserRequest } from '../../core/api/api-types';
import { errorMessage, toProblem } from '../../core/errors/error-messages';
import { applyServerErrors, focusFirstInvalid, notBlank } from '../../core/forms/form-errors';
import { RolesService } from '../../core/roles/roles.service';
import { AppRoutes, isOwner } from '../../core/session/navigation';
import { SessionStore } from '../../core/session/session.store';
import { StaffUsersService } from '../../core/staff/staff-users.service';
import {
  KdActions,
  KdAlert,
  KdBadge,
  KdButton,
  KdDetails,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../design-system';
import { RoleAssignmentsEditor } from './role-assignments/role-assignments-editor';
import {
  AssignmentDraft,
  assignmentRequest,
  describeAssignments,
  draftFromAssignments,
  editorBranches,
  requiresProfessionalRegistration,
} from './role-assignments/role-assignments';
import { ASSIGNMENT_REQUIRED_MESSAGE } from './staff-user-form.page';
import {
  ACCOUNT_STATUS_LABELS,
  ACCOUNT_STATUS_TONES,
  STAFF_LIMITS,
  formatDateTime,
  fullName,
} from './staff-labels';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; user: StaffUser; roles: Role[] | null }
  | { kind: 'failed'; message: string; notFound: boolean };

type AccessAction = 'resend' | 'disable' | 'enable';

export const REGISTRATION_NEEDED_FOR_CLINICAL_ROLE =
  'Para asignar un rol clínico, primero guarda el registro profesional en los datos personales.';
export const REGISTRATION_NEEDED_WHILE_CLINICAL =
  'Tiene un rol clínico asignado: el registro profesional es obligatorio.';

/**
 * A staff user (/staff/:id): personal data, branch role assignments and access (resend the
 * invitation, disable, enable). Each part opens only with its permission of GET /me, and
 * nobody changes their own record (the API enforces all of it again).
 */
@Component({
  selector: 'mk-staff-user-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RoleAssignmentsEditor,
    KdActions,
    KdAlert,
    KdBadge,
    KdButton,
    KdDetails,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './staff-user-detail.page.html',
  styleUrl: './staff-user-detail.page.scss',
})
export class StaffUserDetailPage implements OnInit {
  private readonly staff = inject(StaffUsersService);
  private readonly rolesService = inject(RolesService);
  private readonly session = inject(SessionStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Route parameter :id. */
  readonly id = input.required<string>();

  protected readonly staffLink = AppRoutes.staff;
  protected readonly limits = STAFF_LIMITS;
  protected readonly statusLabels = ACCOUNT_STATUS_LABELS;
  protected readonly statusTones = ACCOUNT_STATUS_TONES;
  protected readonly fullName = fullName;
  protected readonly describeAssignments = describeAssignments;

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly notice = signal<string | null>(null);

  protected readonly user = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.user : null;
  });
  /** Name of the person once loaded; a generic title while loading or after a failure. */
  protected readonly heading = computed(() => {
    const person = this.user();
    return person ? fullName(person) : 'Persona del personal';
  });
  protected readonly roles = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? (current.roles ?? []) : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current : null;
  });

  private readonly permissions = this.session.permissions;
  protected readonly isSelf = computed(() => this.user()?.id === this.session.account()?.accountId);
  protected readonly canManage = computed(
    () => this.permissions().has('identity.users.manage') && !this.isSelf(),
  );
  protected readonly canAssign = computed(
    () =>
      this.permissions().has('identity.assignments.manage') &&
      this.permissions().has('roles.read') &&
      !this.isSelf(),
  );
  protected readonly callerIsOwner = computed(() => isOwner(this.session.account()));
  protected readonly invitationExpiry = computed(() =>
    formatDateTime(this.user()?.invitationExpiresAt),
  );
  protected readonly branches = computed(() =>
    editorBranches(
      this.session.account()?.availableBranches ?? [],
      this.user()?.roleAssignments ?? [],
    ),
  );
  /** True while the saved assignments include a clinical role (roles known only with roles.read). */
  private readonly hasClinicalRole = computed(() => {
    const clinical = new Set(
      this.roles()
        .filter((role) => role.clinical)
        .map((role) => role.id),
    );
    return (this.user()?.roleAssignments ?? []).some((a) => clinical.has(a.role.id));
  });

  // ------------------------------------------------------------------ personal data
  protected readonly form = inject(NonNullableFormBuilder).group({
    firstNames: ['', [Validators.required, Validators.maxLength(STAFF_LIMITS.name), notBlank]],
    lastNames: ['', [Validators.required, Validators.maxLength(STAFF_LIMITS.name), notBlank]],
    jobTitle: ['', [Validators.required, Validators.maxLength(STAFF_LIMITS.jobTitle), notBlank]],
    professionalRegistration: [
      '',
      [Validators.maxLength(STAFF_LIMITS.professionalRegistration), notBlank],
    ],
  });
  protected readonly saving = signal(false);
  protected readonly dataFailure = signal<string | null>(null);
  protected readonly versionConflict = signal(false);

  // ------------------------------------------------------------------ assignments
  protected readonly draft = signal<AssignmentDraft>(draftFromAssignments([], []));
  protected readonly assignmentError = signal<string | null>(null);
  protected readonly assignmentFailure = signal<string | null>(null);
  protected readonly savingAssignments = signal(false);

  // ------------------------------------------------------------------ access
  protected readonly confirmingDisable = signal(false);
  protected readonly accessBusy = signal<AccessAction | null>(null);
  protected readonly accessFailure = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.resetMessages();
    const withRoles =
      this.permissions().has('roles.read') && this.permissions().has('identity.assignments.manage');
    forkJoin({
      user: this.staff.get(this.id()),
      roles: withRoles ? this.rolesService.list() : of(null),
    }).subscribe({
      next: ({ user, roles }) => {
        this.state.set({ kind: 'ready', user, roles });
        this.fill(user);
      },
      error: (error: unknown) =>
        this.state.set({
          kind: 'failed',
          message: errorMessage(error),
          notFound: toProblem(error)?.code === 'NOT_FOUND',
        }),
    });
  }

  // ------------------------------------------------------------------ personal data
  protected save(): void {
    const user = this.user();
    if (!user || this.saving() || !this.canManage()) {
      return;
    }
    this.resetMessages();
    const registration = this.form.controls.professionalRegistration;
    if (this.hasClinicalRole() && !registration.value.trim()) {
      registration.setErrors({ server: REGISTRATION_NEEDED_WHILE_CLINICAL });
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusError('[data-testid="data-error"]', true);
      return;
    }
    const request = changedFields(user, this.form.getRawValue());
    if (!request) {
      this.notice.set('No hay cambios para guardar.');
      return;
    }
    this.saving.set(true);
    this.staff.update(user.id, request).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.replaceUser(saved);
        this.notice.set(`Se guardaron los datos de ${fullName(saved)}.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        const code = toProblem(error)?.code;
        if (code === 'VALIDATION') {
          applyServerErrors(this.form, error);
        } else if (code === 'VERSION_CONFLICT') {
          this.versionConflict.set(true);
        }
        this.dataFailure.set(errorMessage(error));
        this.focusError('[data-testid="data-error"]', true);
      },
    });
  }

  // ------------------------------------------------------------------ assignments
  protected changeDraft(draft: AssignmentDraft): void {
    this.draft.set(draft);
    this.assignmentError.set(null);
  }

  protected saveAssignments(): void {
    const user = this.user();
    if (!user || this.savingAssignments() || !this.canAssign()) {
      return;
    }
    this.resetMessages();
    const request = assignmentRequest(this.draft());
    if (!request) {
      this.assignmentError.set(ASSIGNMENT_REQUIRED_MESSAGE);
      this.focusError('[data-testid="assignments-editor"] [aria-invalid="true"]');
      return;
    }
    if (
      requiresProfessionalRegistration(this.draft(), this.roles()) &&
      !user.professionalRegistration
    ) {
      this.assignmentError.set(REGISTRATION_NEEDED_FOR_CLINICAL_ROLE);
      this.focusError('[data-testid="assignments-editor"] [aria-invalid="true"]');
      return;
    }
    this.savingAssignments.set(true);
    this.staff.replaceAssignments(user.id, request).subscribe({
      next: (roleAssignments) => {
        this.savingAssignments.set(false);
        this.replaceUser({ ...user, roleAssignments });
        this.notice.set(`Se guardaron los roles de ${fullName(user)}.`);
      },
      error: (error: unknown) => {
        this.savingAssignments.set(false);
        const problem = toProblem(error);
        if (problem?.code === 'VALIDATION' && problem.errors?.length) {
          this.assignmentError.set('Revisa los roles elegidos: alguno no es válido.');
        }
        this.assignmentFailure.set(errorMessage(error));
        this.focusError('[data-testid="assignments-failure"]');
      },
    });
  }

  // ------------------------------------------------------------------ access
  protected resendInvitation(): void {
    const user = this.user();
    if (!user || this.accessBusy() || !this.canManage()) {
      return;
    }
    this.resetMessages();
    this.accessBusy.set('resend');
    this.staff.resendInvitation(user.id).subscribe({
      next: () => {
        this.accessBusy.set(null);
        this.notice.set(
          `Enviamos una nueva invitación a ${user.email}. Los enlaces anteriores ya no sirven.`,
        );
        // The new invitation has a new expiry.
        this.reloadUser();
      },
      error: (error: unknown) => this.accessError(error),
    });
  }

  protected askDisable(): void {
    this.resetMessages();
    this.confirmingDisable.set(true);
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector<HTMLElement>('[data-testid="confirm-disable"]')
          ?.focus(),
      { injector: this.injector },
    );
  }

  protected cancelDisable(): void {
    this.confirmingDisable.set(false);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>('[data-testid="disable"]')?.focus(),
      { injector: this.injector },
    );
  }

  protected confirmDisable(): void {
    const user = this.user();
    if (!user || this.accessBusy() || !this.canManage()) {
      return;
    }
    this.accessBusy.set('disable');
    this.staff.disable(user.id).subscribe({
      next: (saved) => {
        this.accessBusy.set(null);
        this.confirmingDisable.set(false);
        this.replaceUser(saved);
        this.notice.set(`Se deshabilitó el acceso de ${fullName(saved)}.`);
      },
      error: (error: unknown) => {
        this.confirmingDisable.set(false);
        this.accessError(error);
      },
    });
  }

  protected enable(): void {
    const user = this.user();
    if (!user || this.accessBusy() || !this.canManage()) {
      return;
    }
    this.resetMessages();
    this.accessBusy.set('enable');
    this.staff.enable(user.id).subscribe({
      next: (saved) => {
        this.accessBusy.set(null);
        this.replaceUser(saved);
        this.notice.set(`Se habilitó el acceso de ${fullName(saved)}.`);
      },
      error: (error: unknown) => this.accessError(error),
    });
  }

  // ------------------------------------------------------------------ helpers
  private accessError(error: unknown): void {
    this.accessBusy.set(null);
    this.accessFailure.set(errorMessage(error));
    this.focusError('[data-testid="access-failure"]');
  }

  private reloadUser(): void {
    const user = this.user();
    if (!user) {
      return;
    }
    this.staff.get(user.id).subscribe({
      next: (fresh) => this.replaceUser(fresh),
      // The invitation was sent; a failed refresh only leaves the old expiry on screen.
      error: () => undefined,
    });
  }

  /** Keeps the roles already loaded and refills the forms with the saved data. */
  private replaceUser(user: StaffUser): void {
    const current = this.state();
    const roles = current.kind === 'ready' ? current.roles : null;
    this.state.set({ kind: 'ready', user, roles });
    this.fill(user);
  }

  private fill(user: StaffUser): void {
    this.form.reset({
      firstNames: user.firstNames,
      lastNames: user.lastNames,
      jobTitle: user.jobTitle,
      professionalRegistration: user.professionalRegistration ?? '',
    });
    if (this.canManage()) {
      this.form.enable();
    } else {
      this.form.disable();
    }
    this.draft.set(draftFromAssignments(user.roleAssignments, this.branches()));
  }

  private resetMessages(): void {
    this.notice.set(null);
    this.dataFailure.set(null);
    this.versionConflict.set(false);
    this.assignmentError.set(null);
    this.assignmentFailure.set(null);
    this.accessFailure.set(null);
  }

  /** Focuses the first invalid field of the personal data form, or else the given element. */
  private focusError(selector: string, preferInvalidField = false): void {
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        const form = host.querySelector<HTMLElement>('[data-testid="data-form"]');
        if (preferInvalidField && form?.querySelector('[aria-invalid="true"]')) {
          focusFirstInvalid(form);
          return;
        }
        host.querySelector<HTMLElement>(selector)?.focus();
      },
      { injector: this.injector },
    );
  }
}

/**
 * PATCH body with only the fields that changed plus the version, or null when nothing changed.
 * An emptied professional registration is sent as null (removed).
 */
export function changedFields(
  user: StaffUser,
  value: {
    firstNames: string;
    lastNames: string;
    jobTitle: string;
    professionalRegistration: string;
  },
): UpdateStaffUserRequest | null {
  const request: UpdateStaffUserRequest = { version: user.version };
  const firstNames = value.firstNames.trim();
  const lastNames = value.lastNames.trim();
  const jobTitle = value.jobTitle.trim();
  const registration = value.professionalRegistration.trim() || null;
  if (firstNames !== user.firstNames) {
    request.firstNames = firstNames;
  }
  if (lastNames !== user.lastNames) {
    request.lastNames = lastNames;
  }
  if (jobTitle !== user.jobTitle) {
    request.jobTitle = jobTitle;
  }
  if (registration !== (user.professionalRegistration ?? null)) {
    request.professionalRegistration = registration;
  }
  return Object.keys(request).length > 1 ? request : null;
}
