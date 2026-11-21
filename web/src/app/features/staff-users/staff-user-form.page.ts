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
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import type { Role } from '../../core/api/api-types';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../core/errors/error-messages';
import { applyServerErrors, focusFirstInvalid, notBlank } from '../../core/forms/form-errors';
import { NoticeService } from '../../core/notice/notice.service';
import { RolesService } from '../../core/roles/roles.service';
import { AppRoutes, isOwner } from '../../core/session/navigation';
import { SessionStore } from '../../core/session/session.store';
import {
  CreationKey,
  StaffUsersService,
  newCreationKey,
} from '../../core/staff/staff-users.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../design-system';
import { RoleAssignmentsEditor } from './role-assignments/role-assignments-editor';
import {
  AssignmentDraft,
  EMPTY_DRAFT,
  assignmentRequest,
  editorBranches,
  requiresProfessionalRegistration,
} from './role-assignments/role-assignments';
import { STAFF_LIMITS, fullName } from './staff-labels';

type LoadState =
  { kind: 'loading' } | { kind: 'ready'; roles: Role[] } | { kind: 'failed'; message: string };

export const ASSIGNMENT_REQUIRED_MESSAGE = 'Elige un rol para al menos una sucursal.';

/** Registers a staff user and sends the invitation (/staff/new). */
@Component({
  selector: 'mk-staff-user-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RoleAssignmentsEditor,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './staff-user-form.page.html',
  styleUrl: './staff-form.scss',
})
export class StaffUserFormPage {
  private readonly staff = inject(StaffUsersService);
  private readonly rolesService = inject(RolesService);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly staffLink = AppRoutes.staff;
  protected readonly limits = STAFF_LIMITS;
  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly assignmentError = signal<string | null>(null);
  protected readonly draft = signal<AssignmentDraft>(EMPTY_DRAFT);

  protected readonly callerIsOwner = computed(() => isOwner(this.session.account()));
  protected readonly branches = computed(() =>
    editorBranches(this.session.account()?.availableBranches ?? []),
  );
  protected readonly roles = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.roles : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly registrationRequired = computed(() =>
    requiresProfessionalRegistration(this.draft(), this.roles()),
  );

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstNames: ['', [Validators.required, Validators.maxLength(STAFF_LIMITS.name), notBlank]],
    lastNames: ['', [Validators.required, Validators.maxLength(STAFF_LIMITS.name), notBlank]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(STAFF_LIMITS.email)]],
    jobTitle: ['', [Validators.required, Validators.maxLength(STAFF_LIMITS.jobTitle), notBlank]],
    professionalRegistration: [
      '',
      [Validators.maxLength(STAFF_LIMITS.professionalRegistration), notBlank],
    ],
  });

  /** Kept across retries of the same data, so a lost response never creates the user twice. */
  private creationKey: CreationKey | null = null;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.rolesService.list().subscribe({
      next: (roles) => this.state.set({ kind: 'ready', roles }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected changeDraft(draft: AssignmentDraft): void {
    this.draft.set(draft);
    this.assignmentError.set(null);
  }

  protected submit(): void {
    if (this.submitting() || this.state().kind !== 'ready') {
      return;
    }
    this.failure.set(null);
    const registration = this.form.controls.professionalRegistration;
    if (this.registrationRequired() && !registration.value.trim()) {
      registration.setErrors({ ...registration.errors, required: true });
    }
    const assignments = assignmentRequest(this.draft());
    this.assignmentError.set(assignments ? null : ASSIGNMENT_REQUIRED_MESSAGE);
    if (this.form.invalid || !assignments) {
      this.form.markAllAsTouched();
      this.focusFirstError();
      return;
    }

    const value = this.form.getRawValue();
    const professionalRegistration = value.professionalRegistration.trim();
    this.creationKey ??= newCreationKey();
    this.submitting.set(true);
    this.staff
      .create(
        {
          firstNames: value.firstNames.trim(),
          lastNames: value.lastNames.trim(),
          email: value.email.trim(),
          jobTitle: value.jobTitle.trim(),
          ...(professionalRegistration ? { professionalRegistration } : {}),
          roleAssignments: assignments,
        },
        this.creationKey,
      )
      .subscribe({
        next: (user) => {
          this.submitting.set(false);
          this.notices.show(
            `Se registró a ${fullName(user)} y le enviamos la invitación a ${user.email}.`,
          );
          void this.router.navigateByUrl(AppRoutes.staff);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (!isRetryable(error)) {
            // The API answered: corrected data must go with a new key.
            this.creationKey = null;
          }
          this.showError(error);
        },
      });
  }

  private showError(error: unknown): void {
    const problem = toProblem(error);
    if (problem?.code === 'EMAIL_TAKEN') {
      const email = this.form.controls.email;
      email.setErrors({ server: ERROR_MESSAGES.EMAIL_TAKEN });
      email.markAsTouched();
    } else if (problem?.code === 'VALIDATION') {
      const unmatched = applyServerErrors(this.form, error);
      if (unmatched.some((fieldError) => fieldError.field.startsWith('roleAssignments'))) {
        this.assignmentError.set('Revisa los roles elegidos: alguno no es válido.');
      }
    }
    this.failure.set(errorMessage(error));
    this.focusFirstError();
  }

  private focusFirstError(): void {
    afterNextRender(
      () => {
        const host = this.host.nativeElement;
        if (host.querySelector('[aria-invalid="true"]')) {
          focusFirstInvalid(host);
        } else {
          host.querySelector<HTMLElement>('[data-testid="form-error"]')?.focus();
        }
      },
      { injector: this.injector },
    );
  }
}

/** Network failures and server errors may have reached the API: retry with the same key. */
export function isRetryable(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}
