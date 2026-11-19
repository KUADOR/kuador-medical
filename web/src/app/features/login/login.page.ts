import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  signal,
  Injector,
  afterNextRender,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppRoutes, landingUrl, safeReturnUrl } from '../../core/auth/navigation';
import { GENERIC_ERROR_MESSAGE, errorMessage } from '../../core/errors/error-messages';
import { controlErrorMessage, focusFirstInvalid } from '../../core/forms/form-errors';
import { NoticeService } from '../../core/notice/notice.service';

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
  protected readonly notice = inject(NoticeService).take();
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

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
      const account = await this.auth.login(this.form.getRawValue());
      if (!account) {
        this.auth.logout();
        this.failure.set(GENERIC_ERROR_MESSAGE);
        return;
      }
      const target = account.mustChangePassword
        ? landingUrl(account)
        : (safeReturnUrl(this.returnUrl()) ?? landingUrl(account));
      await this.router.navigateByUrl(target);
    } catch (error: unknown) {
      this.form.controls.password.reset();
      this.failure.set(errorMessage(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
