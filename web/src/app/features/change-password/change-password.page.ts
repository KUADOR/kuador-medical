import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  Injector,
  afterNextRender,
} from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { AuthRoutes } from '../../core/auth/auth-routes';
import { AuthService } from '../../core/auth/auth.service';
import { landingUrl } from '../../core/auth/navigation';
import { SessionStore } from '../../core/auth/session.store';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../core/errors/error-messages';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  applyServerErrors,
  controlErrorMessage,
  focusFirstInvalid,
  matchingFields,
} from '../../core/forms/form-errors';

type PasswordField = 'currentPassword' | 'newPassword' | 'confirmPassword';

export const CURRENT_PASSWORD_WRONG_MESSAGE = 'La contraseña actual no es correcta.';

/** Group validator: the new password must differ from the current one (error on newPassword). */
const differentFromCurrent: ValidatorFn = (group): ValidationErrors | null => {
  const target = group.get('newPassword');
  if (!target) {
    return null;
  }
  const same = target.value !== '' && target.value === group.get('currentPassword')?.value;
  const { samePassword: _previous, ...others } = target.errors ?? {};
  if (same) {
    target.setErrors({ ...others, samePassword: true });
  } else if (target.hasError('samePassword')) {
    target.setErrors(Object.keys(others).length ? others : null);
  }
  return null;
};

/** Mandatory password change: the only page an account can use until it sets a new password. */
@Component({
  selector: 'mk-change-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './change-password.page.html',
})
export class ChangePasswordPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly account = inject(SessionStore).account;
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      currentPassword: ['', [Validators.required, Validators.maxLength(PASSWORD_MAX_LENGTH)]],
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
    { validators: [differentFromCurrent, matchingFields('newPassword', 'confirmPassword')] },
  );

  protected error(name: PasswordField): string | null {
    const control = this.form.controls[name];
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
    const { currentPassword, newPassword } = this.form.getRawValue();
    try {
      const account = await this.auth.changePassword({ currentPassword, newPassword });
      await this.router.navigateByUrl(landingUrl(account));
    } catch (error: unknown) {
      this.showError(error);
    } finally {
      this.submitting.set(false);
    }
  }

  protected async logout(): Promise<void> {
    this.auth.logout();
    await this.router.navigateByUrl(AuthRoutes.login);
  }

  private showError(error: unknown): void {
    const code = toProblem(error)?.code;
    if (code === 'INVALID_CREDENTIALS') {
      this.setServerError('currentPassword', CURRENT_PASSWORD_WRONG_MESSAGE);
      this.failure.set(CURRENT_PASSWORD_WRONG_MESSAGE);
    } else if (code === 'WEAK_PASSWORD') {
      this.setServerError('newPassword', ERROR_MESSAGES.WEAK_PASSWORD);
      this.failure.set(ERROR_MESSAGES.WEAK_PASSWORD);
    } else {
      if (code === 'VALIDATION') {
        applyServerErrors(this.form, error);
      }
      this.failure.set(errorMessage(error));
    }
    afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
  }

  private setServerError(name: PasswordField, message: string): void {
    const control = this.form.controls[name];
    control.setErrors({ server: message });
    control.markAsTouched();
  }
}
