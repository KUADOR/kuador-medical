import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdLoadingState,
  KdPage,
} from '../../../design-system';
import { takeLinkToken } from '../one-time-link';

export const EMAIL_VERIFIED_MESSAGE =
  'Tu correo quedó verificado y tu cuenta está activa. Ya puedes ingresar.';

type State = 'verifying' | 'verified' | 'invalid' | 'error';

/** Target of the self-registration email (/email-verification#t=<token>): activates the account. */
@Component({
  selector: 'mk-verify-email-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, KdActions, KdAlert, KdButton, KdLoadingState, KdPage],
  template: `
    <kd-page heading="Verificar correo" headingId="verify-title" width="narrow">
      @switch (state()) {
        @case ('verifying') {
          <kd-loading-state label="Verificando tu correo…" data-testid="loading" />
        }
        @case ('verified') {
          <kd-alert tone="ok" data-testid="notice">{{ verifiedMessage }}</kd-alert>
        }
        @case ('invalid') {
          <kd-alert tone="error" data-testid="link-error">{{ invalidMessage }}</kd-alert>
        }
        @case ('error') {
          <kd-alert tone="error" data-testid="form-error">{{ failure() }}</kd-alert>
          <kd-actions>
            <button kdButton type="button" icon="refresh-cw" (click)="verify()">
              Intentar de nuevo
            </button>
          </kd-actions>
        }
      }
      <kd-actions>
        <a
          kdButton
          [variant]="state() === 'verified' ? 'primary' : 'tertiary'"
          [routerLink]="loginLink"
          >Ir a ingresar</a
        >
      </kd-actions>
    </kd-page>
  `,
})
export class VerifyEmailPage {
  private readonly auth = inject(AuthService);
  private readonly token = takeLinkToken(AuthRoutes.verifyEmail);

  protected readonly loginLink = AuthRoutes.login;
  protected readonly verifiedMessage = EMAIL_VERIFIED_MESSAGE;
  protected readonly invalidMessage = ERROR_MESSAGES.ONE_TIME_TOKEN_INVALID;
  protected readonly state = signal<State>(this.token ? 'verifying' : 'invalid');
  protected readonly failure = signal<string | null>(null);

  constructor() {
    void this.verify();
  }

  protected async verify(): Promise<void> {
    if (!this.token) {
      return;
    }
    this.state.set('verifying');
    try {
      await this.auth.confirmEmail(this.token);
      this.state.set('verified');
    } catch (error: unknown) {
      if (toProblem(error)?.code === 'ONE_TIME_TOKEN_INVALID') {
        this.state.set('invalid');
      } else {
        this.failure.set(errorMessage(error));
        this.state.set('error');
      }
    }
  }
}
