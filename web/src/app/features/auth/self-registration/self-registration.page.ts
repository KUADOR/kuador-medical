import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  input,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import type { SelfRegistrationRequest } from '../../../core/api/api-types';
import {
  CONSENT_TEXT_VERSION,
  DOCUMENT_TYPES,
  SEX_OPTIONS,
} from '../../../core/catalogs/patient-catalogs';
import { errorMessage, toProblem } from '../../../core/errors/error-messages';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  applyServerErrors,
  controlErrorMessage,
  e164Phone,
  focusFirstInvalid,
  matchingFields,
  notBlank,
  notInFuture,
} from '../../../core/forms/form-errors';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { KdActions, KdAlert, KdButton, KdField, KdInput, KdPage } from '../../../design-system';

/** Same text whether the person is new or already registered: never reveals patients. */
export const SELF_REGISTRATION_DONE_MESSAGE =
  'Recibimos tu registro. Te enviamos un correo para verificar tu dirección; abre el enlace para activar tu cuenta.';
export const SELF_REGISTRATION_UNAVAILABLE_MESSAGE =
  'El registro de pacientes en línea no está disponible para esta clínica. Comunícate con la clínica para registrarte.';

type Field =
  | 'documentType'
  | 'documentNumber'
  | 'firstNames'
  | 'lastNames'
  | 'birthDate'
  | 'sex'
  | 'email'
  | 'phone'
  | 'address'
  | 'password'
  | 'confirmPassword'
  | 'consent';

/**
 * Public self-registration of an adult patient through the clinic public code
 * (/clinics/:publicCode/register). Available only when the clinic enabled it (404 otherwise).
 */
@Component({
  selector: 'mk-self-registration-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdInput,
    KdPage,
  ],
  templateUrl: './self-registration.page.html',
  styleUrl: './self-registration.page.scss',
})
export class SelfRegistrationPage {
  private readonly auth = inject(AuthService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** :publicCode of the route, bound through withComponentInputBinding(). */
  readonly publicCode = input.required<string>();

  protected readonly loginLink = AuthRoutes.login;
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly sexOptions = SEX_OPTIONS;
  protected readonly consentVersion = CONSENT_TEXT_VERSION;
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly doneMessage = SELF_REGISTRATION_DONE_MESSAGE;
  protected readonly unavailableMessage = SELF_REGISTRATION_UNAVAILABLE_MESSAGE;
  protected readonly state = signal<'form' | 'done' | 'unavailable'>('form');
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);

  /** Same client id and Idempotency-Key while retrying after a network error. */
  private attempt = newAttempt();

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      documentType: [DOCUMENT_TYPES[0]?.code ?? '', [Validators.required]],
      documentNumber: ['', [Validators.required, Validators.maxLength(30), notBlank]],
      firstNames: ['', [Validators.required, Validators.maxLength(120), notBlank]],
      lastNames: ['', [Validators.required, Validators.maxLength(120), notBlank]],
      birthDate: ['', [Validators.required, notInFuture]],
      sex: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      phone: ['', [Validators.maxLength(20), e164Phone]],
      address: ['', [Validators.maxLength(250)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(PASSWORD_MIN_LENGTH),
          Validators.maxLength(PASSWORD_MAX_LENGTH),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
      consent: [false, [Validators.requiredTrue]],
    },
    { validators: matchingFields('password', 'confirmPassword') },
  );

  protected error(name: Field): string | null {
    const control = this.form.controls[name];
    if (!control.touched) {
      return null;
    }
    if (name === 'consent' && control.hasError('required')) {
      return 'Debes aceptar el tratamiento de tus datos personales para registrarte.';
    }
    return controlErrorMessage(control);
  }

  protected async submit(): Promise<void> {
    if (this.submitting()) {
      return;
    }
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }
    this.submitting.set(true);
    try {
      await this.auth.selfRegister(this.publicCode(), this.request(), this.attempt.key);
      this.state.set('done');
    } catch (error: unknown) {
      this.handleError(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private request(): SelfRegistrationRequest {
    const value = this.form.getRawValue();
    const optional = (text: string) => (text.trim() ? text.trim() : undefined);
    return {
      id: this.attempt.id,
      documentType: value.documentType,
      documentNumber: value.documentNumber.trim(),
      firstNames: value.firstNames.trim(),
      lastNames: value.lastNames.trim(),
      birthDate: value.birthDate,
      sex: value.sex,
      email: value.email.trim(),
      phone: optional(value.phone),
      address: optional(value.address),
      password: value.password,
      consent: { textVersion: CONSENT_TEXT_VERSION },
    };
  }

  private handleError(error: unknown): void {
    const problem = toProblem(error);
    if (error instanceof HttpErrorResponse && error.status === 404) {
      this.state.set('unavailable');
      return;
    }
    if (!(error instanceof HttpErrorResponse && error.status === 0)) {
      // The server answered: a corrected form is a new request, with a new key.
      this.attempt = newAttempt();
    }
    if (problem?.code === 'VALIDATION') {
      applyServerErrors(this.form, error);
    } else if (problem?.code === 'WEAK_PASSWORD') {
      const control = this.form.controls.password;
      control.setErrors({ server: errorMessage(error) });
      control.markAsTouched();
    }
    this.failure.set(errorMessage(error));
    afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
  }
}

function newAttempt(): { id: string; key: string } {
  return { id: crypto.randomUUID(), key: crypto.randomUUID() };
}
