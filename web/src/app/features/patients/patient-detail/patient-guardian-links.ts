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
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import type { GuardianLink, Patient } from '../../../core/api/api-types';
import { errorMessage } from '../../../core/errors/error-messages';
import {
  applyServerErrors,
  controlErrorMessage,
  focusFirstInvalid,
  notBlank,
} from '../../../core/forms/form-errors';
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
 * patient on their own account, never here.
 */
@Component({
  selector: 'mk-patient-guardian-links',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, GuardianPersonFields, GuardianLinkList],
  template: `
    <section class="card" aria-labelledby="links-title" data-testid="guardian-links">
      <h2 id="links-title">Representantes y vínculos</h2>

      <div aria-live="polite">
        @if (notice(); as message) {
          <p class="alert alert--success" role="status" data-testid="links-notice">{{ message }}</p>
        }
      </div>
      @if (actionFailure(); as message) {
        <p class="alert" role="alert" data-testid="links-error">{{ message }}</p>
      }

      @if (confirming(); as link) {
        <div
          class="alert alert--info"
          role="alertdialog"
          aria-labelledby="end-link-title"
          aria-describedby="end-link-text"
        >
          <h3 id="end-link-title">
            ¿Terminar el vínculo con {{ link.guardian.firstNames }} {{ link.guardian.lastNames }}?
          </h3>
          <p id="end-link-text">
            Quedará en el historial con la fecha de hoy y dejará de dar acceso.
          </p>
          <div class="actions">
            <button
              type="button"
              class="button button--danger"
              data-testid="confirm-end"
              [disabled]="ending()"
              (click)="confirmEnd()"
            >
              {{ ending() ? 'Terminando…' : 'Sí, terminar el vínculo' }}
            </button>
            <button type="button" class="button button--secondary" (click)="cancelEnd()">
              Cancelar
            </button>
          </div>
        </div>
      }

      <div [attr.aria-busy]="state().kind === 'loading'">
        @switch (state().kind) {
          @case ('loading') {
            <p class="hint">Cargando vínculos…</p>
          }
          @case ('failed') {
            <p class="alert" role="alert" data-testid="links-load-error">{{ loadFailure() }}</p>
            <div class="actions">
              <button type="button" class="button" (click)="load()">Volver a intentar</button>
            </div>
          }
          @default {
            <mk-guardian-link-list
              [links]="links()"
              [canEnd]="canManage()"
              emptyMessage="El paciente no tiene representantes ni contactos registrados."
              (end)="askEnd($event)"
            />
          }
        }
      </div>

      @if (canManage()) {
        @if (patient().minor) {
          @if (adding()) {
            <form class="form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
              <fieldset class="fieldset">
                <legend>Agregar representante legal</legend>
                @if (formFailure(); as message) {
                  <p class="alert" role="alert" data-testid="add-error">{{ message }}</p>
                }
                <div class="field">
                  <label for="link-relationship">Parentesco o relación</label>
                  <input
                    id="link-relationship"
                    type="text"
                    formControlName="relationship"
                    autocomplete="off"
                    [attr.aria-invalid]="relationshipError() ? 'true' : null"
                    [attr.aria-describedby]="relationshipError() ? 'link-relationship-error' : null"
                  />
                  @if (relationshipError(); as message) {
                    <p id="link-relationship-error" class="field__error">{{ message }}</p>
                  }
                </div>
                <mk-guardian-person-fields [group]="form.controls.person" idPrefix="link" />
                <div class="actions">
                  <button type="submit" class="button" [disabled]="saving()">
                    {{ saving() ? 'Guardando…' : 'Agregar representante' }}
                  </button>
                  <button type="button" class="button button--secondary" (click)="closeForm()">
                    Cancelar
                  </button>
                </div>
              </fieldset>
            </form>
          } @else {
            <div class="actions">
              <button type="button" class="button" data-testid="add-guardian" (click)="openForm()">
                Agregar representante legal
              </button>
            </div>
          }
        } @else {
          <p class="hint" data-testid="adult-hint">
            El paciente es mayor de edad: solo él puede autorizar un contacto de emergencia o un
            acceso delegado desde su cuenta.
          </p>
        }
      }
    </section>
  `,
})
export class PatientGuardianLinks implements OnInit {
  private readonly patients = inject(PatientsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly patient = input.required<Patient>();
  readonly canManage = input(false);

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
  protected readonly ending = signal(false);

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly formFailure = signal<string | null>(null);
  private attempt = newAttempt();

  protected readonly form = this.fb.group({
    relationship: ['', [Validators.required, Validators.maxLength(60), notBlank]],
    person: guardianPersonGroup(this.fb),
  });
  private readonly formEvents = toSignal(this.form.events);

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

  protected relationshipError(): string | null {
    this.formEvents();
    const control = this.form.controls.relationship;
    return control.touched ? controlErrorMessage(control) : null;
  }

  protected openForm(): void {
    this.notice.set(null);
    this.adding.set(true);
    this.focus('#link-relationship');
  }

  protected closeForm(): void {
    this.adding.set(false);
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
          this.adding.set(false);
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
    this.focus('[data-testid="confirm-end"]');
  }

  protected cancelEnd(): void {
    const link = this.confirming();
    this.confirming.set(null);
    if (link) {
      this.focus(`[data-end-link="${CSS.escape(link.id)}"]`);
    }
  }

  protected confirmEnd(): void {
    const link = this.confirming();
    if (!link || this.ending()) {
      return;
    }
    this.ending.set(true);
    this.patients.endGuardianLink(this.patient().id, link.id).subscribe({
      next: () => {
        this.ending.set(false);
        this.confirming.set(null);
        this.notice.set(
          `Se terminó el vínculo con ${link.guardian.firstNames} ${link.guardian.lastNames}.`,
        );
        this.load();
      },
      error: (error: unknown) => {
        this.ending.set(false);
        this.confirming.set(null);
        this.actionFailure.set(errorMessage(error));
      },
    });
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
