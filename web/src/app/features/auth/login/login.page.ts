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
import { Router, RouterLink } from '@angular/router';
import { GENERIC_ERROR_MESSAGE, errorMessage } from '../../../core/errors/error-messages';
import { controlErrorMessage, focusFirstInvalid } from '../../../core/forms/form-errors';
import { NoticeService } from '../../../core/notice/notice.service';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../../core/session/login-navigation';
import { AppRoutes } from '../../../core/session/navigation';

/**
 * Email and password login. When the password matches several clinics, or one clinic with
 * several branches, it continues on the clinic or branch selection page.
 */
@Component({
  selector: 'mk-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** ?returnUrl= set by the guards, bound through withComponentInputBinding(). */
  readonly returnUrl = input<string | undefined>();

  protected readonly signupLink = AppRoutes.signup;
  protected readonly forgotPasswordLink = AuthRoutes.forgotPassword;
  protected readonly notice = inject(NoticeService).take();
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  constructor() {
    // A new login replaces any clinic or branch choice left half done.
    this.auth.cancelPendingStep();
  }

  protected error(name: 'email' | 'password'): string | null {
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
    try {
      const outcome = await this.auth.login(this.form.getRawValue());
      const next = nextUrlAfterLogin(this.router, outcome, this.returnUrl());
      if (!next) {
        this.auth.logout();
        this.failure.set(GENERIC_ERROR_MESSAGE);
        return;
      }
      await this.router.navigateByUrl(next);
    } catch (error: unknown) {
      // Same generic message for a wrong email and a wrong password (INVALID_CREDENTIALS).
      this.form.controls.password.reset();
      this.failure.set(errorMessage(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
