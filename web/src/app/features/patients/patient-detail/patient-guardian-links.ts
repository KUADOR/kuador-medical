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
  output,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { GuardianLink, Patient } from '../../../core/api/api-types';
import { errorMessage } from '../../../core/errors/error-messages';
import { applyServerErrors, focusFirstInvalid, notBlank } from '../../../core/forms/form-errors';
import { fullName } from '../../../core/patients/patient-labels';
import {
  KdAlert,
  KdButton,
  KdCard,
  KdDialog,
  KdErrorState,
  KdField,
  KdInput,
  KdLoadingState,
} from '../../../design-system';
import { PatientsService, newAttempt } from '../../../core/patients/patients.service';
import {
  GuardianPersonFields,
  guardianPersonGroup,
  toGuardianPerson,
} from '../shared/guardian-person-fields';
import { GuardianLinkList } from '../shared/guardian-link-list';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; links: GuardianLink[] }
  | { kind: 'failed'; message: string };

/**
 * Guardian links of a patient: current and historical (FR-028), adding a legal guardian to a
 * minor and ending a link (patients.guardians.manage). Links of an adult are authorized by the
 * patient on their own account, never here. Ending a link is confirmed in a modal dialog.
 */
@Component({
  selector: 'mk-patient-guardian-links',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    GuardianPersonFields,
    GuardianLinkList,
    KdAlert,
    KdButton,
    KdCard,
    KdDialog,
    KdErrorState,
    KdField,
    KdInput,
    KdLoadingState,
  ],
  templateUrl: './patient-guardian-links.html',
  styleUrl: './patient-guardian-links.scss',
})
export class PatientGuardianLinks implements OnInit {
  private readonly patients = inject(PatientsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly patient = input.required<Patient>();
  readonly canManage = input(false);
  /** Fires when the add guardian form opens or closes (its submit is then the primary action). */
  readonly formOpenChange = output<boolean>();

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly links = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.links : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly notice = signal<string | null>(null);
  protected readonly actionFailure = signal<string | null>(null);
  protected readonly confirming = signal<GuardianLink | null>(null);
  protected readonly dialogOpen = signal(false);
  protected readonly ending = signal(false);
  protected readonly confirmHeading = computed(() => {
    const link = this.confirming();
    return link ? `¿Terminar el vínculo con ${fullName(link.guardian)}?` : 'Terminar el vínculo';
  });

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly formFailure = signal<string | null>(null);
  private attempt = newAttempt();

  protected readonly form = this.fb.group({
    relationship: ['', [Validators.required, Validators.maxLength(60), notBlank]],
    person: guardianPersonGroup(this.fb),
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.patients.guardianLinks(this.patient().id).subscribe({
      next: (links) => this.state.set({ kind: 'loaded', links }),
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected openForm(): void {
    this.notice.set(null);
    this.setAdding(true);
    this.focus('#link-relationship');
  }

  protected closeForm(): void {
    this.setAdding(false);
    this.formFailure.set(null);
    this.form.reset();
    this.focus('[data-testid="add-guardian"]');
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    this.formFailure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }
    const attempt = this.attempt;
    const relationship = this.form.controls.relationship.value.trim();
    this.saving.set(true);
    this.patients
      .addGuardianLink(
        this.patient().id,
        { id: attempt.id, relationship, person: toGuardianPerson(this.form.controls.person) },
        attempt.key,
      )
      .subscribe({
        next: (link) => {
          this.saving.set(false);
          this.attempt = newAttempt();
          this.setAdding(false);
          this.form.reset();
          this.notice.set(
            `Se agregó a ${link.guardian.firstNames} ${link.guardian.lastNames} como representante legal.`,
          );
          this.load();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          if (!(error instanceof HttpErrorResponse && error.status === 0)) {
            this.attempt = newAttempt();
          }
          applyServerErrors(this.form, error);
          this.formFailure.set(errorMessage(error));
          afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
            injector: this.injector,
          });
        },
      });
  }

  protected askEnd(link: GuardianLink): void {
    this.notice.set(null);
    this.actionFailure.set(null);
    this.confirming.set(link);
    this.dialogOpen.set(true);
    this.focus('[data-testid="confirm-end"]');
  }

  protected cancelEnd(): void {
    this.dialogOpen.set(false);
  }

  /** Closed with Cancel, the close button or Escape: back to the button that opened it. */
  protected onDialogClosed(): void {
    const link = this.confirming();
    if (!link || this.ending()) {
      return;
    }
    this.confirming.set(null);
    this.focus(`[data-end-link="${CSS.escape(link.id)}"]`);
  }

  protected confirmEnd(): void {
    const link = this.confirming();
    if (!link || this.ending()) {
      return;
    }
    this.ending.set(true);
    this.patients.endGuardianLink(this.patient().id, link.id).subscribe({
      next: () => {
        this.finishEnd();
        this.notice.set(`Se terminó el vínculo con ${fullName(link.guardian)}.`);
        this.load();
      },
      error: (error: unknown) => {
        this.finishEnd();
        this.actionFailure.set(errorMessage(error));
      },
    });
  }

  private finishEnd(): void {
    this.confirming.set(null);
    this.ending.set(false);
    this.dialogOpen.set(false);
  }

  private setAdding(adding: boolean): void {
    this.adding.set(adding);
    this.formOpenChange.emit(adding);
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
