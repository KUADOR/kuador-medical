import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AcceptInvitationRequest,
  ApiPaths,
  AuthenticatedResult,
  BranchSelectionResult,
  ChangePasswordRequest,
  ClinicSelectionResult,
  CurrentSession,
  InvitationPreview,
  LoginRequest,
  LoginResult,
  OneTimeTokenRequest,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  RefreshRequest,
  SelectBranchRequest,
  SelectClinicRequest,
  SelfRegistrationRequest,
  SessionPaths,
  SwitchBranchRequest,
  TenantSignupRequest,
  TenantSignupResponse,
  selfRegistrationPath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { SessionStore } from './session.store';

/** A login step that still needs a choice of the person. */
export type LoginStep = ClinicSelectionResult | BranchSelectionResult;

/** Result of a login step as seen by the pages. */
export type LoginOutcome =
  { status: 'AUTHENTICATED'; account: CurrentSession | null } | { status: LoginStep['status'] };

interface PendingStep {
  step: LoginStep;
  /** Epoch ms when the pre-auth token expires. */
  expiresAt: number;
}

/** Thrown when a selection is attempted without a pending (or with an expired) login step. */
export class LoginStepExpiredError extends Error {
  constructor() {
    super('The login step expired');
  }
}

/**
 * Authentication flows of the user registration contract: multi-clinic login with clinic and
 * branch selection, branch switching, logout, password reset, invitations, email verification
 * and patient self-registration. Clinic signup and password change stay on the identity contract.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  /** Logout must not trigger a refresh nor carry an expired bearer: it skips the interceptors. */
  private readonly bareHttp = new HttpClient(inject(HttpBackend));
  private readonly config = inject(AppConfigService);
  private readonly session = inject(SessionStore);

  /** Pending clinic or branch choice. Kept in memory only: the pre-auth token is never stored. */
  private readonly pending = signal<PendingStep | null>(null);

  /** POST /auth/login. */
  async login(credentials: LoginRequest): Promise<LoginOutcome> {
    const result = await firstValueFrom(
      this.http.post<LoginResult>(this.url(SessionPaths.login), {
        email: credentials.email.trim(),
        password: credentials.password,
      } satisfies LoginRequest),
    );
    return this.handle(result);
  }

  /** Pending clinic or branch choice, or null when there is none or its token expired. */
  pendingStep(): LoginStep | null {
    const pending = this.pending();
    if (!pending) {
      return null;
    }
    if (pending.expiresAt <= Date.now()) {
      this.pending.set(null);
      return null;
    }
    return pending.step;
  }

  /** Drops a pending clinic or branch choice (e.g. "use another account"). */
  cancelPendingStep(): void {
    this.pending.set(null);
  }

  /** POST /auth/login/clinic with the pending pre-auth token. */
  async selectClinic(clinicId: string): Promise<LoginOutcome> {
    const step = this.requireStep('CLINIC_SELECTION_REQUIRED');
    const result = await firstValueFrom(
      this.http.post<LoginResult>(this.url(SessionPaths.loginClinic), {
        preAuthToken: step.preAuthToken,
        clinicId,
      } satisfies SelectClinicRequest),
    );
    return this.handle(result);
  }

  /** POST /auth/login/branch with the pending pre-auth token. */
  async selectBranch(branchId: string): Promise<LoginOutcome> {
    const step = this.requireStep('BRANCH_SELECTION_REQUIRED');
    const result = await firstValueFrom(
      this.http.post<AuthenticatedResult>(this.url(SessionPaths.loginBranch), {
        preAuthToken: step.preAuthToken,
        branchId,
      } satisfies SelectBranchRequest),
    );
    return this.handle(result);
  }

  /** POST /auth/switch-branch: new tokens and permissions for another branch of the clinic. */
  async switchBranch(branchId: string): Promise<CurrentSession | null> {
    const refreshToken = this.session.refreshToken() ?? undefined;
    const result = await firstValueFrom(
      this.http.post<AuthenticatedResult>(this.url(SessionPaths.switchBranch), {
        branchId,
        refreshToken,
      } satisfies SwitchBranchRequest),
    );
    this.session.start(result);
    return this.session.ensureAccount();
  }

  /**
   * Ends the session in the browser right away and revokes the refresh token in the background
   * (POST /auth/logout always answers 204), so leaving never waits for the network.
   */
  logout(): void {
    const refreshToken = this.session.refreshToken();
    this.pending.set(null);
    this.session.clear();
    if (refreshToken) {
      this.bareHttp
        .post<void>(this.url(SessionPaths.logout), { refreshToken } satisfies RefreshRequest)
        .subscribe({ error: () => undefined });
    }
  }

  /** POST /auth/password-reset/request. Always 202: never reveals whether the email exists. */
  async requestPasswordReset(email: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(this.url(SessionPaths.passwordResetRequest), {
        email: email.trim(),
      } satisfies PasswordResetRequest),
    );
  }

  /** POST /auth/password-reset/confirm with the token of the link. */
  async confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(this.url(SessionPaths.passwordResetConfirm), {
        token,
        newPassword,
      } satisfies PasswordResetConfirmRequest),
    );
  }

  /** POST /auth/invitations/preview: clinic name and masked email of a valid invitation. */
  previewInvitation(token: string): Promise<InvitationPreview> {
    return firstValueFrom(
      this.http.post<InvitationPreview>(this.url(SessionPaths.invitationPreview), {
        token,
      } satisfies OneTimeTokenRequest),
    );
  }

  /** POST /auth/invitations/accept: activates the account and continues as a login step. */
  async acceptInvitation(token: string, password: string): Promise<LoginOutcome> {
    const result = await firstValueFrom(
      this.http.post<LoginResult>(this.url(SessionPaths.invitationAccept), {
        token,
        password,
      } satisfies AcceptInvitationRequest),
    );
    return this.handle(result);
  }

  /** POST /auth/email-verification/confirm for a self-registered patient. */
  async confirmEmail(token: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(this.url(SessionPaths.emailVerification), {
        token,
      } satisfies OneTimeTokenRequest),
    );
  }

  /**
   * POST /public/clinics/{publicCode}/patient-registrations. The client id and the
   * Idempotency-Key make a retry after a network error safe.
   */
  async selfRegister(
    publicCode: string,
    request: SelfRegistrationRequest,
    idempotencyKey: string,
  ): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(this.url(selfRegistrationPath(publicCode)), request, {
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    );
  }

  /**
   * POST /tenants/signup (identity contract). The clinic gets OWNER and ADMIN, both assigned
   * to the creator. It does not log in: the page does it next, so a login failure does not
   * look like a signup failure.
   */
  async signUp(request: TenantSignupRequest): Promise<TenantSignupResponse> {
    const body: TenantSignupRequest = {
      clinicName: request.clinicName.trim(),
      ownerDisplayName: request.ownerDisplayName.trim(),
      email: request.email.trim(),
      password: request.password,
    };
    return firstValueFrom(this.http.post<TenantSignupResponse>(this.url(ApiPaths.signup), body));
  }

  /** POST /auth/password (identity contract), clears the pending change and reloads GET /me. */
  async changePassword(request: ChangePasswordRequest): Promise<CurrentSession | null> {
    await firstValueFrom(this.http.post<void>(this.url(ApiPaths.changePassword), request));
    this.session.markPasswordChanged();
    return this.session.ensureAccount();
  }

  private async handle(result: LoginResult): Promise<LoginOutcome> {
    if (result.status === 'AUTHENTICATED') {
      this.pending.set(null);
      this.session.start(result);
      return { status: 'AUTHENTICATED', account: await this.session.ensureAccount() };
    }
    this.pending.set({ step: result, expiresAt: Date.now() + result.preAuthExpiresIn * 1000 });
    return { status: result.status };
  }

  private requireStep<S extends LoginStep['status']>(status: S): Extract<LoginStep, { status: S }> {
    const step = this.pendingStep();
    if (step?.status !== status) {
      throw new LoginStepExpiredError();
    }
    return step as Extract<LoginStep, { status: S }>;
  }

  private url(path: string): string {
    return this.config.apiUrl(path);
  }
}
