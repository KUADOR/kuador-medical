import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  Injector,
  afterNextRender,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthRoutes } from '../../core/session/auth-routes';
import { AuthService } from '../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../core/session/login-navigation';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../core/errors/error-messages';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  applyServerErrors,
  focusFirstInvalid,
  matchingFields,
  notBlank,
} from '../../core/forms/form-errors';
import { NoticeService } from '../../core/notice/notice.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdPage,
} from '../../design-system';

/** Clinic self-signup: the person who signs up becomes OWNER and ADMIN of the new clinic. */
@Component({
  selector: 'mk-signup-page',
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
  templateUrl: './signup.page.html',
  styleUrl: './signup.page.scss',
})
export class SignupPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly loginLink = AuthRoutes.login;
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      clinicName: ['', [Validators.required, Validators.maxLength(120), notBlank]],
      ownerDisplayName: ['', [Validators.required, Validators.maxLength(120), notBlank]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(PASSWORD_MIN_LENGTH),
          Validators.maxLength(PASSWORD_MAX_LENGTH),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: matchingFields('password', 'confirmPassword') },
  );

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
    const { confirmPassword: _confirm, ...request } = this.form.getRawValue();
    try {
      await this.auth.signUp(request);
    } catch (error: unknown) {
      this.showSignupError(error);
      this.submitting.set(false);
      return;
    }

    try {
      const outcome = await this.auth.login({ email: request.email, password: request.password });
      const next = nextUrlAfterLogin(this.router, outcome, null);
      if (!next) {
        throw new Error('GET /me failed after signup');
      }
      await this.router.navigateByUrl(next);
    } catch {
      // The clinic exists: let the person log in by hand instead of retrying the signup.
      this.auth.logout();
      this.notices.show('Tu clínica quedó registrada. Ingresa con tu correo y contraseña.');
      await this.router.navigateByUrl(AuthRoutes.login);
    } finally {
      this.submitting.set(false);
    }
  }

  private showSignupError(error: unknown): void {
    const code = toProblem(error)?.code;
    if (code === 'EMAIL_TAKEN') {
      const email = this.form.controls.email;
      email.setErrors({ server: ERROR_MESSAGES.EMAIL_TAKEN });
      email.markAsTouched();
    } else if (code === 'VALIDATION') {
      applyServerErrors(this.form, error);
    }
    this.failure.set(errorMessage(error));
    afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
  }
}
