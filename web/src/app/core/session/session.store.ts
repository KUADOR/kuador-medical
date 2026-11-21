import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AuthenticatedResult,
  CurrentSession,
  SessionPermission,
  SessionPaths,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

const STORAGE_KEY = 'mk.session';
/** Refresh a little before the access token expires, so requests never race the expiry. */
export const ACCESS_TOKEN_LEEWAY_MS = 30_000;

export interface StoredSession {
  accessToken: string;
  /** Epoch ms when the access token expires. */
  expiresAt: number;
  refreshToken: string;
  /** Epoch ms when the refresh token expires. */
  refreshExpiresAt: number;
  mustChangePassword: boolean;
}

/**
 * Holds the tokens of the session and the current session from GET /me.
 * Tokens live in sessionStorage (cleared when the tab closes), never in localStorage.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  private readonly tokens = signal<StoredSession | null>(readStoredSession());
  private readonly current = signal<CurrentSession | null>(null);
  /** In-flight GET /me, shared by concurrent callers. */
  private loading: Promise<CurrentSession | null> | null = null;
  /** Bumped whenever the session changes, so a stale GET /me never fills a newer session. */
  private generation = 0;

  /** Current session of GET /me (account, clinic, branch, permissions and modules). */
  readonly account = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.tokens() !== null);
  readonly mustChangePassword = computed(
    () => this.current()?.mustChangePassword ?? this.tokens()?.mustChangePassword ?? false,
  );
  readonly permissions = computed(() => new Set(this.current()?.permissions ?? []));
  readonly modules = computed(() => new Set(this.current()?.enabledModules ?? []));

  /** Access token while it is still valid (with leeway), otherwise null. */
  accessToken(): string | null {
    const session = this.tokens();
    if (!session) {
      return null;
    }
    return session.expiresAt - ACCESS_TOKEN_LEEWAY_MS > Date.now() ? session.accessToken : null;
  }

  /** Refresh token while it is still valid; clears the session when it expired. */
  refreshToken(): string | null {
    const session = this.tokens();
    if (!session) {
      return null;
    }
    if (session.refreshExpiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return session.refreshToken;
  }

  /** True when there is a session that can still send requests, directly or after a refresh. */
  hasUsableSession(): boolean {
    return this.accessToken() !== null || this.refreshToken() !== null;
  }

  /** Starts a session from an AUTHENTICATED login step (login, clinic, branch, invitation). */
  start(result: AuthenticatedResult): void {
    this.store(result, result.mustChangePassword);
    this.resetAccount();
  }

  /**
   * Replaces the tokens after POST /auth/refresh without touching the loaded GET /me:
   * the clinic and branch stay the same.
   */
  replaceTokens(result: AuthenticatedResult): void {
    this.store(result, result.mustChangePassword);
  }

  /**
   * Loads GET /me once per session; returns null when not authenticated or the call fails.
   * Concurrent callers (the shell and a guard after a reload) share the same request.
   */
  ensureAccount(): Promise<CurrentSession | null> {
    if (!this.hasUsableSession()) {
      return Promise.resolve(null);
    }
    const cached = this.current();
    if (cached) {
      return Promise.resolve(cached);
    }
    this.loading ??= this.fetchAccount().finally(() => (this.loading = null));
    return this.loading;
  }

  private async fetchAccount(): Promise<CurrentSession | null> {
    const generation = this.generation;
    try {
      const account = await firstValueFrom(
        this.http.get<CurrentSession>(this.config.apiUrl(SessionPaths.me)),
      );
      if (generation !== this.generation) {
        return null;
      }
      this.current.set(account);
      return account;
    } catch {
      return null;
    }
  }

  /** Forces the next guard to reload GET /me (e.g. after a password change). */
  invalidateAccount(): void {
    this.resetAccount();
  }

  /** Marks the session as blocked until the password is changed (403 PASSWORD_CHANGE_REQUIRED). */
  markPasswordChangeRequired(): void {
    this.updateMustChange(true);
    const account = this.current();
    if (account) {
      this.current.set({ ...account, mustChangePassword: true });
    }
  }

  /** Clears the pending password change after POST /auth/password and reloads GET /me next time. */
  markPasswordChanged(): void {
    this.updateMustChange(false);
    this.resetAccount();
  }

  hasPermission(code: SessionPermission): boolean {
    return this.permissions().has(code);
  }

  hasModule(module: string): boolean {
    return this.modules().has(module);
  }

  clear(): void {
    this.tokens.set(null);
    this.resetAccount();
    sessionStorage.removeItem(STORAGE_KEY);
  }

  private store(result: AuthenticatedResult, mustChangePassword: boolean): void {
    const now = Date.now();
    this.persist({
      accessToken: result.accessToken,
      expiresAt: now + result.expiresIn * 1000,
      refreshToken: result.refreshToken,
      refreshExpiresAt: now + result.refreshExpiresIn * 1000,
      mustChangePassword,
    });
  }

  private updateMustChange(value: boolean): void {
    const session = this.tokens();
    if (session) {
      this.persist({ ...session, mustChangePassword: value });
    }
  }

  private persist(next: StoredSession): void {
    this.tokens.set(next);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  private resetAccount(): void {
    this.generation++;
    this.loading = null;
    this.current.set(null);
  }
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<StoredSession>;
    if (
      typeof parsed.accessToken !== 'string' ||
      typeof parsed.expiresAt !== 'number' ||
      typeof parsed.refreshToken !== 'string' ||
      typeof parsed.refreshExpiresAt !== 'number'
    ) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    if (parsed.refreshExpiresAt <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return {
      accessToken: parsed.accessToken,
      expiresAt: parsed.expiresAt,
      refreshToken: parsed.refreshToken,
      refreshExpiresAt: parsed.refreshExpiresAt,
      mustChangePassword: parsed.mustChangePassword === true,
    };
  } catch {
    return null;
  }
}
