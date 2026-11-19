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
import { Router, RouterLink } from '@angular/router';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  controlErrorMessage,
  focusFirstInvalid,
  matchingFields,
} from '../../../core/forms/form-errors';
import { NoticeService } from '../../../core/notice/notice.service';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { takeLinkToken } from '../one-time-link';

export const PASSWORD_RESET_DONE_MESSAGE =
  'Tu contraseña quedó actualizada. Ingresa con tu correo y la contraseña nueva.';

/** Target of the password reset email: sets a new password once, for one clinic. */
@Component({
  selector: 'mk-reset-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.page.html',
})
export class ResetPasswordPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly token = takeLinkToken(AuthRoutes.resetPassword);

  protected readonly forgotPasswordLink = AuthRoutes.forgotPassword;
  protected readonly loginLink = AuthRoutes.login;
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  /** Missing, expired or used link: the form is replaced by a message and a way to ask again. */
  protected readonly invalidLink = signal(this.token === null);
  protected readonly invalidLinkMessage = ERROR_MESSAGES.ONE_TIME_TOKEN_INVALID;
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      newPassword: [
        '',
        [
          Validators.required,
          Validators.minLength(PASSWORD_MIN_LENGTH),
          Validators.maxLength(PASSWORD_MAX_LENGTH),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: matchingFields('newPassword', 'confirmPassword') },
  );

  protected error(name: 'newPassword' | 'confirmPassword'): string | null {
    const control = this.form.controls[name];
    return control.touched ? controlErrorMessage(control) : null;
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || !this.token) {
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
      await this.auth.confirmPasswordReset(this.token, this.form.controls.newPassword.value);
      this.notices.show(PASSWORD_RESET_DONE_MESSAGE);
      await this.router.navigateByUrl(AuthRoutes.login);
    } catch (error: unknown) {
      const code = toProblem(error)?.code;
      if (code === 'ONE_TIME_TOKEN_INVALID') {
        this.invalidLink.set(true);
        return;
      }
      if (code === 'WEAK_PASSWORD') {
        const control = this.form.controls.newPassword;
        control.setErrors({ server: ERROR_MESSAGES.WEAK_PASSWORD });
        control.markAsTouched();
      }
      this.failure.set(errorMessage(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
