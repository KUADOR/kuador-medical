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
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';
import type { AuthorizeLinkRequest, GuardianLink } from '../../../core/api/api-types';
import { errorMessage } from '../../../core/errors/error-messages';
import {
  applyServerErrors,
  controlErrorMessage,
  focusFirstInvalid,
  notBlank,
} from '../../../core/forms/form-errors';
import { LINK_TYPE_LABELS, fullName } from '../../../core/patients/patient-labels';
import { PatientsService, newAttempt } from '../../../core/patients/patients.service';
import { GuardianLinkList } from '../shared/guardian-link-list';
import {
  GuardianPersonFields,
  guardianPersonGroup,
  toGuardianPerson,
} from '../shared/guardian-person-fields';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; links: GuardianLink[] }
  | { kind: 'failed'; message: string };

type AuthorizedType = AuthorizeLinkRequest['linkType'];

/** Value of the person choice for someone new (not in the link history). */
export const NEW_PERSON = 'new';

/** One option per person of the history (e.g. the former guardian), newest link first. */
export function knownPeople(
  links: readonly GuardianLink[],
): { id: string; name: string; relationship: string }[] {
  const seen = new Map<string, { id: string; name: string; relationship: string }>();
  for (const link of [...links].sort((a, b) => b.validFrom.localeCompare(a.validFrom))) {
    if (!seen.has(link.guardian.id)) {
      seen.set(link.guardian.id, {
        id: link.guardian.id,
        name: fullName(link.guardian),
        relationship: link.relationship,
      });
    }
  }
  return [...seen.values()];
}

/**
 * Adult patient page (/my-contacts): their current and historical guardian links and the
 * explicit authorization of an emergency contact or delegated access (FR-028).
 */
@Component({
  selector: 'mk-my-guardian-links-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, GuardianLinkList, GuardianPersonFields],
  templateUrl: './my-guardian-links.page.html',
})
export class MyGuardianLinksPage {
  private readonly patients = inject(PatientsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly newPerson = NEW_PERSON;
  protected readonly linkTypes: readonly { code: AuthorizedType; label: string; hint: string }[] = [
    {
      code: 'EMERGENCY_CONTACT',
      label: LINK_TYPE_LABELS.EMERGENCY_CONTACT,
      hint: 'La clínica puede llamarle en una emergencia. No ve tu información.',
    },
    {
      code: 'DELEGATED_ACCESS',
      label: LINK_TYPE_LABELS.DELEGATED_ACCESS,
      hint: 'Puede hacer trámites en tu nombre con el alcance que autorizas.',
    },
  ];

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly links = computed(() => {
    const current = this.state();
    return current.kind === 'loaded' ? current.links : [];
  });
  protected readonly loadFailure = computed(() => {
    const current = this.state();
    return current.kind === 'failed' ? current.message : null;
  });
  protected readonly people = computed(() => knownPeople(this.links()));

  protected readonly notice = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly saving = signal(false);
  private attempt = newAttempt();

  protected readonly form = this.fb.group({
    personChoice: [NEW_PERSON, [Validators.required]],
    linkType: ['' as AuthorizedType | '', [Validators.required]],
    relationship: ['', [Validators.required, Validators.maxLength(60), notBlank]],
    person: guardianPersonGroup(this.fb),
  });
  private readonly formEvents = toSignal(this.form.events);
  protected readonly choice = toSignal(
    this.form.controls.personChoice.valueChanges.pipe(
      startWith(this.form.controls.personChoice.value),
    ),
    { requireSync: true },
  );

  constructor() {
    this.form.controls.personChoice.valueChanges.subscribe((choice) => this.applyChoice(choice));
    this.load();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.patients.myGuardianLinks().subscribe({
      next: (links) => {
        this.state.set({ kind: 'loaded', links });
        // Offer the most recent person of the history (usually the former guardian) first.
        const first = knownPeople(links)[0];
        this.form.controls.personChoice.setValue(first ? first.id : NEW_PERSON);
      },
      error: (error: unknown) => this.state.set({ kind: 'failed', message: errorMessage(error) }),
    });
  }

  protected error(name: 'personChoice' | 'linkType' | 'relationship'): string | null {
    this.formEvents();
    const control = this.form.controls[name];
    if (!control.touched) {
      return null;
    }
    if (name === 'linkType' && control.hasError('required')) {
      return 'Elige qué autorizas.';
    }
    return controlErrorMessage(control);
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    this.failure.set(null);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }
    const value = this.form.getRawValue();
    const attempt = this.attempt;
    const request: AuthorizeLinkRequest = {
      id: attempt.id,
      linkType: value.linkType as AuthorizedType,
      relationship: value.relationship.trim(),
      ...(value.personChoice === NEW_PERSON
        ? { person: toGuardianPerson(this.form.controls.person) }
        : { guardianId: value.personChoice }),
    };
    this.saving.set(true);
    this.patients.authorizeMyGuardianLink(request, attempt.key).subscribe({
      next: (link) => {
        this.saving.set(false);
        this.attempt = newAttempt();
        this.form.controls.linkType.reset('');
        this.form.controls.relationship.reset('');
        this.form.controls.person.reset();
        this.notice.set(
          `Autorizaste a ${fullName(link.guardian)} como ${LINK_TYPE_LABELS[link.linkType].toLowerCase()}.`,
        );
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (!(error instanceof HttpErrorResponse && error.status === 0)) {
          this.attempt = newAttempt();
        }
        applyServerErrors(this.form, error);
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
      },
    });
  }

  /** A person of the history needs no identification; a new one does. */
  private applyChoice(choice: string): void {
    const person = this.form.controls.person;
    if (choice === NEW_PERSON) {
      person.enable({ emitEvent: false });
    } else {
      person.disable({ emitEvent: false });
      const known = this.people().find((p) => p.id === choice);
      const relationship = this.form.controls.relationship;
      if (known && !relationship.value) {
        relationship.setValue(known.relationship);
      }
    }
  }
}
