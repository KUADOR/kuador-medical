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
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { GENERIC_ERROR_MESSAGE } from '../../../core/errors/error-messages';
import { focusFirstInvalid } from '../../../core/forms/form-errors';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../../core/session/login-navigation';
import { KdActions, KdAlert, KdButton, KdField, KdInput, KdPage } from '../../../design-system';
import {
  LOST_DEVICE_HELP,
  codeFailure,
  createCodeControl,
  normalizedCode,
} from './two-factor-code';

/**
 * Second step of the login of an account with TOTP (spec FR-010, ADR-019): every login of the
 * OWNER asks for the current code of the authenticator app before opening the session.
 */
@Component({
  selector: 'mk-two-factor-verify-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, KdActions, KdAlert, KdButton, KdField, KdInput, KdPage],
  templateUrl: './two-factor-verify.page.html',
  styleUrl: './two-factor.page.scss',
})
export class TwoFactorVerifyPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** ?returnUrl= kept from the guards and the login, bound through withComponentInputBinding(). */
  readonly returnUrl = input<string | undefined>();

  protected readonly lostDeviceHelp = LOST_DEVICE_HELP;
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = new FormGroup({ code: createCodeControl() });
  protected readonly code = this.form.controls.code;

  protected async submit(): Promise<void> {
    if (this.submitting()) {
      return;
    }
    this.failure.set(null);
    if (this.code.invalid) {
      this.code.markAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
        injector: this.injector,
      });
      return;
    }
    this.submitting.set(true);
    try {
      const outcome = await this.auth.verifyTwoFactor(normalizedCode(this.code));
      const next = nextUrlAfterLogin(this.router, outcome, this.returnUrl());
      if (!next) {
        this.auth.logout();
        this.failure.set(GENERIC_ERROR_MESSAGE);
        return;
      }
      await this.router.navigateByUrl(next);
    } catch (error: unknown) {
      this.failure.set(codeFailure(error, this.code));
      if (!this.failure()) {
        afterNextRender(() => focusFirstInvalid(this.host.nativeElement), {
          injector: this.injector,
        });
      }
    } finally {
      this.submitting.set(false);
    }
  }

  protected async logout(): Promise<void> {
    this.auth.logout();
    await this.router.navigateByUrl(AuthRoutes.login);
  }
}
