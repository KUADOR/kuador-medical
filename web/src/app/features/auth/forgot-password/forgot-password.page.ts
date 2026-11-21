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
import { focusFirstInvalid } from '../../../core/forms/form-errors';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdPage,
} from '../../../design-system';

/** Same text whether or not the email exists: the page never reveals registered accounts. */
export const RESET_REQUESTED_MESSAGE =
  'Si el correo corresponde a una cuenta, te enviamos un enlace para crear una contraseña nueva. Si tienes cuenta en varias clínicas, recibirás un enlace por cada una. Revisa también tu carpeta de correo no deseado.';

/** Requests a password reset link by email (one per clinic where the email has an account). */
@Component({
  selector: 'mk-forgot-password-page',
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
  template: `
    <kd-page
      heading="Recuperar contraseña"
      headingId="forgot-title"
      width="narrow"
      [description]="sent() ? null : 'Escribe el correo de tu cuenta y te enviaremos un enlace.'"
    >
      @if (sent()) {
        <kd-alert tone="ok" data-testid="notice">{{ sentMessage }}</kd-alert>
      } @else {
        <form class="account-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (failure(); as message) {
            <kd-alert tone="error" data-testid="form-error">{{ message }}</kd-alert>
          }
          <kd-field label="Correo">
            <input
              kdInput
              id="forgot-email"
              type="email"
              formControlName="email"
              autocomplete="username"
              inputmode="email"
            />
          </kd-field>
          <kd-actions>
            <button kdButton variant="primary" type="submit" [disabled]="submitting()">
              {{ submitting() ? 'Enviando…' : 'Enviar enlace' }}
            </button>
          </kd-actions>
        </form>
      }

      <kd-actions>
        <a kdButton variant="tertiary" [routerLink]="loginLink">Volver a ingresar</a>
      </kd-actions>
    </kd-page>
  `,
  styleUrl: './forgot-password.page.scss',
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
