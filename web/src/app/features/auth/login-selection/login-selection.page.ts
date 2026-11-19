import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import type { BranchSummary, ClinicSummary } from '../../../core/api/api-types';
import {
  GENERIC_ERROR_MESSAGE,
  errorMessage,
  toProblem,
} from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import { AuthRoutes } from '../../../core/session/auth-routes';
import { AuthService, LoginStep, LoginStepExpiredError } from '../../../core/session/auth.service';
import { nextUrlAfterLogin } from '../../../core/session/login-navigation';

export const LOGIN_STEP_EXPIRED_MESSAGE =
  'Tu inicio de sesión venció. Vuelve a ingresar con tu correo y contraseña.';

/**
 * Second step of the login: choose the clinic (the password matched several clinics) or the
 * branch (the account has assignments in several branches). Route data `step` picks which.
 */
@Component({
  selector: 'mk-login-selection-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './login-selection.page.html',
})
export class LoginSelectionPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notices = inject(NoticeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /** Bound from route data. */
  readonly step = input.required<LoginStep['status']>();
  /** ?returnUrl= carried from the login form. */
  readonly returnUrl = input<string | undefined>();

  protected readonly loginLink = AuthRoutes.login;
  protected readonly submitting = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  private readonly pending = signal<LoginStep | null>(this.auth.pendingStep());

  protected readonly isClinic = computed(() => this.step() === 'CLINIC_SELECTION_REQUIRED');
  protected readonly heading = computed(() =>
    this.isClinic() ? 'Elige la clínica' : 'Elige la sucursal',
  );
  protected readonly hint = computed(() => {
    const pending = this.pending();
    if (pending?.status === 'BRANCH_SELECTION_REQUIRED') {
      return `Tienes acceso a varias sucursales de ${pending.clinic.name}. Elige en cuál vas a trabajar; puedes cambiarla después.`;
    }
    return 'Tu correo y contraseña coinciden en varias clínicas. Elige a cuál quieres ingresar.';
  });
  protected readonly options = computed<(ClinicSummary | BranchSummary)[]>(() => {
    const pending = this.pending();
    if (!pending) {
      return [];
    }
    return pending.status === 'CLINIC_SELECTION_REQUIRED' ? pending.clinics : pending.branches;
  });

  protected async choose(id: string): Promise<void> {
    if (this.submitting()) {
      return;
    }
    this.failure.set(null);
    this.submitting.set(id);
    try {
      const outcome = this.isClinic()
        ? await this.auth.selectClinic(id)
        : await this.auth.selectBranch(id);
      const next = nextUrlAfterLogin(this.router, outcome, this.returnUrl());
      if (!next) {
        this.auth.logout();
        this.showFailure(GENERIC_ERROR_MESSAGE);
        return;
      }
      await this.router.navigateByUrl(next);
    } catch (error: unknown) {
      if (
        error instanceof LoginStepExpiredError ||
        toProblem(error)?.code === 'INVALID_SESSION_TOKEN'
      ) {
        this.auth.cancelPendingStep();
        this.notices.show(LOGIN_STEP_EXPIRED_MESSAGE);
        await this.router.navigateByUrl(AuthRoutes.login);
        return;
      }
      this.showFailure(errorMessage(error));
    } finally {
      this.submitting.set(null);
    }
  }

  protected cancel(): void {
    this.auth.cancelPendingStep();
  }

  private showFailure(message: string): void {
    this.failure.set(message);
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>('[role="alert"]')?.focus(),
      {
        injector: this.injector,
      },
    );
  }
}
