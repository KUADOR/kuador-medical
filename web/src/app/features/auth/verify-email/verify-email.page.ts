import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { takeLinkToken } from '../one-time-link';

export const EMAIL_VERIFIED_MESSAGE =
  'Tu correo quedó verificado y tu cuenta está activa. Ya puedes ingresar.';

type State = 'verifying' | 'verified' | 'invalid' | 'error';

/** Target of the self-registration email (/email-verification#t=<token>): activates the account. */
@Component({
  selector: 'mk-verify-email-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="page page--narrow" aria-labelledby="verify-title">
      <h1 id="verify-title">Verificar correo</h1>
      @switch (state()) {
        @case ('verifying') {
          <p role="status" data-testid="loading">Verificando tu correo…</p>
        }
        @case ('verified') {
          <p class="alert alert--success" role="status" data-testid="notice">
            {{ verifiedMessage }}
          </p>
        }
        @case ('invalid') {
          <p class="alert" role="alert" data-testid="link-error">{{ invalidMessage }}</p>
        }
        @case ('error') {
          <p class="alert" role="alert" data-testid="form-error">{{ failure() }}</p>
          <div class="actions">
            <button type="button" class="button button--secondary" (click)="verify()">
              Intentar de nuevo
            </button>
          </div>
        }
      }
      <p><a [routerLink]="loginLink">Ir a ingresar</a></p>
    </section>
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
