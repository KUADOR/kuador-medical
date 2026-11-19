import { HttpBackend, HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthenticatedResult, RefreshRequest, SessionPaths } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { SessionStore } from './session.store';

/**
 * Rotates the refresh token (POST /auth/refresh) once at a time: concurrent requests that find
 * an expired access token, or get a 401, wait for the same refresh instead of each sending one
 * (a second use of a rotated refresh token would revoke the whole session).
 */
@Injectable({ providedIn: 'root' })
export class TokenRefresher {
  // HttpBackend skips the interceptors: the refresh never carries the bearer nor loops.
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly config = inject(AppConfigService);
  private readonly session = inject(SessionStore);

  private inFlight: Promise<string | null> | null = null;

  /** Valid access token, refreshing it first when it expired. Null when there is no session. */
  async validAccessToken(): Promise<string | null> {
    return this.session.accessToken() ?? (await this.refresh());
  }

  /**
   * Gets a new access token. When `rejected` is given (the token a 401 came back for) and the
   * session already holds a different one, another request refreshed meanwhile: reuse it.
   */
  refresh(rejected?: string): Promise<string | null> {
    const current = this.session.accessToken();
    if (rejected && current && current !== rejected) {
      return Promise.resolve(current);
    }
    this.inFlight ??= this.rotate().finally(() => (this.inFlight = null));
    return this.inFlight;
  }

  private async rotate(): Promise<string | null> {
    const refreshToken = this.session.refreshToken();
    if (!refreshToken) {
      return null;
    }
    try {
      const result = await firstValueFrom(
        this.http.post<AuthenticatedResult>(this.config.apiUrl(SessionPaths.refresh), {
          refreshToken,
        } satisfies RefreshRequest),
      );
      this.session.replaceTokens(result);
      return result.accessToken;
    } catch (error: unknown) {
      // 400/401: the refresh token is revoked, expired or unknown, the session is over.
      // Network or server errors keep the session so a later request can try again.
      if (error instanceof HttpErrorResponse && (error.status === 400 || error.status === 401)) {
        this.session.clear();
      }
      return null;
    }
  }
}
