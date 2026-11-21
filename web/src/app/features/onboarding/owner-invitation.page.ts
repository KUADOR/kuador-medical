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
import type { OwnerInvitationPreview } from '../../core/api/api-types';
import { ERROR_MESSAGES, errorMessage, toProblem } from '../../core/errors/error-messages';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  focusFirstInvalid,
  matchingFields,
  mustAccept,
} from '../../core/forms/form-errors';
import { AuthRoutes } from '../../core/session/auth-routes';
import { AuthService } from '../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../core/session/login-navigation';
import { formatDay } from '../../core/subscription/subscription-notice';
import {
  KdActions,
  KdAlert,
  KdButton,
  KdField,
  KdInput,
  KdLoadingState,
  KdPage,
} from '../../design-system';
import { takeLinkToken } from '../auth/one-time-link';

export const OWNER_INVITATION_INVALID_MESSAGE =
  'La invitación no es válida: venció, ya se usó o fue reemplazada por una más reciente. Pide a Kuador que te la reenvíe.';
export const OUTDATED_TERMS_MESSAGE =
  'Los términos o la política de datos se actualizaron mientras completabas el formulario. Revisa las versiones vigentes y acéptalas de nuevo.';

type State = 'loading' | 'ready' | 'invalid' | 'error';

/**
 * Target of the OWNER invitation email (/owner-invitation#t=<token>, spec FR-008): shows the
 * clinic, lets the OWNER choose a password and accept the terms and the data policy, and
 * continues to the mandatory TOTP enrollment (FR-010).
 */
@Component({
  selector: 'mk-owner-invitation-page',
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
  templateUrl: './owner-invitation.page.html',
  styleUrl: './owner-invitation.page.scss',
})
export class OwnerInvitationPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly token = takeLinkToken(AuthRoutes.ownerInvitation);

  protected readonly loginLink = AuthRoutes.login;
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  protected readonly invalidMessage = OWNER_INVITATION_INVALID_MESSAGE;
  protected readonly formatDay = formatDay;
  protected readonly state = signal<State>(this.token ? 'loading' : 'invalid');
  protected readonly loadError = signal<string | null>(null);
  protected readonly preview = signal<OwnerInvitationPreview | null>(null);
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
      acceptTerms: [false, [mustAccept]],
      acceptDataPolicy: [false, [mustAccept]],
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
      this.preview.set(await this.auth.previewOwnerInvitation(this.token));
      this.state.set('ready');
    } catch (error: unknown) {
      if (toProblem(error)?.code === 'INVITATION_INVALID_OR_EXPIRED') {
        this.state.set('invalid');
      } else {
        this.loadError.set(errorMessage(error));
        this.state.set('error');
      }
    }
  }

  protected async submit(): Promise<void> {
    const invitation = this.preview();
    if (this.submitting() || !this.token || !invitation) {
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
      const outcome = await this.auth.acceptOwnerInvitation({
        token: this.token,
        password: this.form.controls.password.value,
        acceptTerms: true,
        termsVersion: invitation.termsVersion,
        acceptDataPolicy: true,
        dataPolicyVersion: invitation.dataPolicyVersion,
      });
      const next = nextUrlAfterLogin(this.router, outcome, null);
      if (next) {
        await this.router.navigateByUrl(next);
      }
    } catch (error: unknown) {
      await this.handleFailure(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private async handleFailure(error: unknown): Promise<void> {
    const problem = toProblem(error);
    if (problem?.code === 'INVITATION_INVALID_OR_EXPIRED') {
      this.state.set('invalid');
      return;
    }
    if (problem?.code === 'WEAK_PASSWORD') {
      const control = this.form.controls.password;
      control.setErrors({ server: ERROR_MESSAGES.WEAK_PASSWORD });
      control.markAsTouched();
    }
    const outdated = problem?.errors?.some(
      (item) => item.field === 'termsVersion' || item.field === 'dataPolicyVersion',
    );
    if (problem?.code === 'VALIDATION' && outdated) {
      // New versions were published: show them and ask for the acceptance again.
      this.form.controls.acceptTerms.reset(false);
      this.form.controls.acceptDataPolicy.reset(false);
      await this.load();
      this.failure.set(OUTDATED_TERMS_MESSAGE);
      return;
    }
    this.failure.set(errorMessage(error));
  }
}
