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
import type { InvitationPreview } from '../../../core/api/api-types';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../../core/errors/error-messages';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  focusFirstInvalid,
  matchingFields,
} from '../../../core/forms/form-errors';
import { NoticeService } from '../../../core/notice/notice.service';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService } from '../../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../../core/session/login-navigation';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../../design-system';
import { takeLinkToken } from '../one-time-link';

export const INVITATION_INVALID_MESSAGE =
  'La invitación no es válida: venció o ya fue usada. Pide a la administración de tu clínica que te la reenvíe.';

type State = 'loading' | 'ready' | 'invalid' | 'error';

/**
 * Target of the invitation email (/invitation#t=<token>): shows the clinic and the masked email
 * of the invitation, lets the person choose a password and logs them in.
 */
@Component({
  selector: 'mk-accept-invitation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    KdActions,
    KdAlert,
    KdButton,
    KdField,
    KdInput,
    KdLoadingState,
    KdPage,
  ],
  templateUrl: './accept-invitation.page.html',
  styleUrl: './accept-invitation.page.scss',
})
export class AcceptInvitationPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly token = takeLinkToken(AuthRoutes.invitation);

  protected readonly loginLink = AuthRoutes.login;
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly invalidMessage = INVITATION_INVALID_MESSAGE;
  protected readonly state = signal<State>(this.token ? 'loading' : 'invalid');
  protected readonly loadError = signal<string | null>(null);
  protected readonly preview = signal<InvitationPreview | null>(null);
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
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

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    if (!this.token) {
      return;
    }
    this.state.set('loading');
    try {
      this.preview.set(await this.auth.previewInvitation(this.token));
      this.state.set('ready');
    } catch (error: unknown) {
      if (toProblem(error)?.code === 'ONE_TIME_TOKEN_INVALID') {
        this.state.set('invalid');
      } else {
        this.loadError.set(errorMessage(error));
        this.state.set('error');
      }
    }
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
      const outcome = await this.auth.acceptInvitation(
        this.token,
        this.form.controls.password.value,
      );
      const next = nextUrlAfterLogin(this.router, outcome, null);
      if (!next) {
        // The account is active: the person can log in by hand.
        this.auth.logout();
        this.notices.show('Tu cuenta quedó activa. Ingresa con tu correo y tu contraseña nueva.');
        await this.router.navigateByUrl(AuthRoutes.login);
        return;
      }
      await this.router.navigateByUrl(next);
    } catch (error: unknown) {
      const code = toProblem(error)?.code;
      if (code === 'ONE_TIME_TOKEN_INVALID') {
        this.state.set('invalid');
        return;
      }
      if (code === 'WEAK_PASSWORD') {
        const control = this.form.controls.password;
        control.setErrors({ server: ERROR_MESSAGES.WEAK_PASSWORD });
        control.markAsTouched();
      }
      this.failure.set(errorMessage(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
