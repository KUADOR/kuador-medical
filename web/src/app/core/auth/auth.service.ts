import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ApiPaths,
  ChangePasswordRequest,
  CurrentAccount,
  LoginRequest,
  LoginResponse,
  TenantSignupRequest,
  TenantSignupResponse,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { SessionStore } from './session.store';

/** Login, clinic self-signup, password change and logout against the identity contract. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);
  private readonly session = inject(SessionStore);

  /** POST /auth/login, starts the session and loads GET /me. */
  async login(credentials: LoginRequest): Promise<CurrentAccount | null> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(this.config.apiUrl(ApiPaths.login), {
        email: credentials.email.trim(),
        password: credentials.password,
      } satisfies LoginRequest),
    );
    this.session.start(response);
    return this.session.ensureAccount();
  }

  /**
   * POST /tenants/signup. The clinic gets OWNER and ADMIN, both assigned to the creator.
   * It does not log in: the page does it next, so a login failure does not look like a signup failure.
   */
  async signUp(request: TenantSignupRequest): Promise<TenantSignupResponse> {
    const body: TenantSignupRequest = {
      clinicName: request.clinicName.trim(),
      ownerDisplayName: request.ownerDisplayName.trim(),
      email: request.email.trim(),
      password: request.password,
    };
    return firstValueFrom(
      this.http.post<TenantSignupResponse>(this.config.apiUrl(ApiPaths.signup), body),
    );
  }

  /** POST /auth/password, clears the pending change and reloads GET /me. */
  async changePassword(request: ChangePasswordRequest): Promise<CurrentAccount | null> {
    await firstValueFrom(
      this.http.post<void>(this.config.apiUrl(ApiPaths.changePassword), request),
    );
    this.session.markPasswordChanged();
    return this.session.ensureAccount();
  }

  /** There is no logout endpoint in the skeleton: the token is dropped from the browser. */
  logout(): void {
    this.session.clear();
  }
}
