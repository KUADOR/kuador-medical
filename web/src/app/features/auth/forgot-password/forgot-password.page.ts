import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../../core/errors/error-messages';
import { controlErrorMessage, focusFirstInvalid } from '../../../core/forms/form-errors';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';

/** Same text whether or not the email exists: the page never reveals registered accounts. */
export const RESET_REQUESTED_MESSAGE =
  'Si el correo corresponde a una cuenta, te enviamos un enlace para crear una contraseña nueva. Si tienes cuenta en varias clínicas, recibirás un enlace por cada una. Revisa también tu carpeta de correo no deseado.';

/** Requests a password reset link by email (one per clinic where the email has an account). */
@Component({
  selector: 'mk-forgot-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page page--narrow" aria-labelledby="forgot-title">
      <h1 id="forgot-title">Recuperar contraseña</h1>

      @if (sent()) {
        <p class="alert alert--success" role="status" data-testid="notice">{{ sentMessage }}</p>
      } @else {
        <p class="hint">Escribe el correo de tu cuenta y te enviaremos un enlace.</p>
        <form class="form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (failure(); as message) {
            <p class="alert" role="alert" data-testid="form-error">{{ message }}</p>
          }
          <div class="field">
            <label for="forgot-email">Correo</label>
            <input
              id="forgot-email"
              type="email"
              formControlName="email"
              autocomplete="username"
              inputmode="email"
              [attr.aria-invalid]="error() ? 'true' : null"
              [attr.aria-describedby]="error() ? 'forgot-email-error' : null"
            />
            @if (error(); as message) {
              <p id="forgot-email-error" class="field__error">{{ message }}</p>
            }
          </div>
          <div class="actions">
            <button type="submit" class="button" [disabled]="submitting()">
              {{ submitting() ? 'Enviando…' : 'Enviar enlace' }}
            </button>
          </div>
        </form>
      }

      <p><a [routerLink]="loginLink">Volver a ingresar</a></p>
    </section>
  `,
})
export class ForgotPasswordPage {
  private readonly auth = inject(AuthService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly loginLink = AuthRoutes.login;
  protected readonly sentMessage = RESET_REQUESTED_MESSAGE;
  protected readonly submitting = signal(false);
  protected readonly sent = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected error(): string | null {
    const control = this.form.controls.email;
    return control.touched ? controlErrorMessage(control) : null;
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
      await this.auth.requestPasswordReset(this.form.controls.email.value);
      this.sent.set(true);
    } catch (error: unknown) {
      // Only throttling, validation or server errors reach here; never "unknown email".
      this.failure.set(errorMessage(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
