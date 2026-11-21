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
import type { TwoFactorEnrollment } from '../../../core/api/api-types';
import {
  GENERIC_ERROR_MESSAGE,
  errorMessage,
  toProblem,
} from '../../../core/errors/error-messages';
import { focusFirstInvalid } from '../../../core/forms/form-errors';
import { AuthRoutes, RETURN_URL_PARAM } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../../core/session/login-navigation';
import { SessionStore } from '../../../core/session/session.store';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../../design-system';
import { QrCode } from './qr-code';
import {
  LOST_DEVICE_HELP,
  codeFailure,
  createCodeControl,
  normalizedCode,
} from './two-factor-code';

type State = 'loading' | 'ready' | 'error';

/** Groups the Base32 secret in blocks of 4 so it can be typed by hand without mistakes. */
export function groupSecret(secret: string): string {
  return (
    secret
      .replace(/=+$/, '')
      .match(/.{1,4}/g)
      ?.join(' ') ?? secret
  );
}

/**
 * Mandatory TOTP enrollment of an OWNER (spec FR-010, ADR-019): shows the QR code and the
 * secret of a new enrollment and enables the second factor with a first code. Until then the
 * session can do nothing else.
 */
@Component({
  selector: 'mk-two-factor-enroll-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
    QrCode,
  ],
  templateUrl: './two-factor-enroll.page.html',
  styleUrl: './two-factor.page.scss',
})
export class TwoFactorEnrollPage {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** ?returnUrl= kept from the guards and the login, bound through withComponentInputBinding(). */
  readonly returnUrl = input<string | undefined>();

  protected readonly lostDeviceHelp = LOST_DEVICE_HELP;
  protected readonly state = signal<State>('loading');
  protected readonly loadError = signal<string | null>(null);
  protected readonly enrollment = signal<TwoFactorEnrollment | null>(null);
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = new FormGroup({ code: createCodeControl() });
  protected readonly code = this.form.controls.code;
  protected readonly groupSecret = groupSecret;

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.enrollment.set(await this.auth.startTwoFactorEnrollment());
      this.state.set('ready');
    } catch (error: unknown) {
      if (await this.goToVerificationIfEnabled(error)) {
        return;
      }
      this.loadError.set(errorMessage(error));
      this.state.set('error');
    }
  }

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
      const outcome = await this.auth.confirmTwoFactorEnrollment(normalizedCode(this.code));
      const next = nextUrlAfterLogin(this.router, outcome, this.returnUrl());
      if (!next) {
        this.auth.logout();
        this.failure.set(GENERIC_ERROR_MESSAGE);
        return;
      }
      await this.router.navigateByUrl(next);
    } catch (error: unknown) {
      if (await this.goToVerificationIfEnabled(error)) {
        return;
      }
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

  /** 409 TWO_FACTOR_ALREADY_ENABLED: the account already has it, so it only has to verify. */
  private async goToVerificationIfEnabled(error: unknown): Promise<boolean> {
    if (toProblem(error)?.code !== 'TWO_FACTOR_ALREADY_ENABLED') {
      return false;
    }
    this.session.markTwoFactorRequired('VERIFICATION_REQUIRED');
    const returnUrl = this.returnUrl();
    await this.router.navigate([AuthRoutes.twoFactorVerify], {
      queryParams: returnUrl ? { [RETURN_URL_PARAM]: returnUrl } : {},
    });
    return true;
  }
}
