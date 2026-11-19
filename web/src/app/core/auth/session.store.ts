import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiPaths, CurrentAccount, LoginResponse, PermissionCode } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

const STORAGE_KEY = 'mk.session';

interface StoredSession {
  accessToken: string;
  expiresAt: number;
  mustChangePassword: boolean;
}

/**
 * Holds the access token and the current account.
 * The token lives in sessionStorage (cleared when the tab closes) and never in localStorage.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  private readonly session = signal<StoredSession | null>(readStoredSession());
  private readonly currentAccount = signal<CurrentAccount | null>(null);
  /** In-flight GET /me, shared by concurrent callers. */
  private loading: Promise<CurrentAccount | null> | null = null;
  /** Bumped whenever the session changes, so a stale GET /me never fills a newer session. */
  private generation = 0;

  readonly account = this.currentAccount.asReadonly();
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly mustChangePassword = computed(
    () => this.currentAccount()?.mustChangePassword ?? this.session()?.mustChangePassword ?? false,
  );
  readonly permissions = computed(() => new Set(this.currentAccount()?.permissions ?? []));

  /** Token to send, or null when there is none or it already expired. */
  accessToken(): string | null {
    const current = this.session();
    if (!current) {
      return null;
    }
    if (current.expiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return current.accessToken;
  }

  /** Starts a session from a successful login response. */
  start(login: LoginResponse): void {
    const next: StoredSession = {
      accessToken: login.accessToken,
      expiresAt: Date.now() + login.expiresIn * 1000,
      mustChangePassword: login.mustChangePassword,
    };
    this.session.set(next);
    this.resetAccount();
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  /**
   * Loads GET /me once per session; returns null when not authenticated or the call fails.
   * Concurrent callers (the shell and a guard after a reload) share the same request.
   */
  ensureAccount(): Promise<CurrentAccount | null> {
    if (!this.accessToken()) {
      return Promise.resolve(null);
    }
    const cached = this.currentAccount();
    if (cached) {
      return Promise.resolve(cached);
    }
    this.loading ??= this.fetchAccount().finally(() => (this.loading = null));
    return this.loading;
  }

  private async fetchAccount(): Promise<CurrentAccount | null> {
    const generation = this.generation;
    try {
      const account = await firstValueFrom(
        this.http.get<CurrentAccount>(this.config.apiUrl(ApiPaths.me)),
      );
      if (generation !== this.generation) {
        return null;
      }
      this.currentAccount.set(account);
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
    const current = this.session();
    if (current) {
      const next = { ...current, mustChangePassword: true };
      this.session.set(next);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    }
    const account = this.currentAccount();
    if (account) {
      this.currentAccount.set({ ...account, mustChangePassword: true });
    }
  }

  /** Clears the pending password change after POST /auth/password and reloads GET /me next time. */
  markPasswordChanged(): void {
    const current = this.session();
    if (current) {
      const next = { ...current, mustChangePassword: false };
      this.session.set(next);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    }
    this.resetAccount();
  }

  hasPermission(code: PermissionCode): boolean {
    return this.permissions().has(code);
  }

  clear(): void {
    this.session.set(null);
    this.resetAccount();
    sessionStorage.removeItem(STORAGE_KEY);
  }

  private resetAccount(): void {
    this.generation++;
    this.loading = null;
    this.currentAccount.set(null);
  }
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<StoredSession>;
    if (typeof parsed.accessToken !== 'string' || typeof parsed.expiresAt !== 'number') {
      return null;
    }
    if (parsed.expiresAt <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return {
      accessToken: parsed.accessToken,
      expiresAt: parsed.expiresAt,
      mustChangePassword: parsed.mustChangePassword === true,
    };
  } catch {
    return null;
  }
}
