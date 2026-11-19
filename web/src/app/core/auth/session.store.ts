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
    this.currentAccount.set(null);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  /** Loads GET /me once per session; returns null when not authenticated or the call fails. */
  async ensureAccount(): Promise<CurrentAccount | null> {
    if (!this.accessToken()) {
      return null;
    }
    const cached = this.currentAccount();
    if (cached) {
      return cached;
    }
    try {
      const account = await firstValueFrom(
        this.http.get<CurrentAccount>(this.config.apiUrl(ApiPaths.me)),
      );
      this.currentAccount.set(account);
      return account;
    } catch {
      return null;
    }
  }

  /** Forces the next guard to reload GET /me (e.g. after a password change). */
  invalidateAccount(): void {
    this.currentAccount.set(null);
  }

  /** Marks the session as blocked until the password is changed (403 PASSWORD_CHANGE_REQUIRED). */
  markPasswordChangeRequired(): void {
    const current = this.session();
    if (current) {
      this.session.set({ ...current, mustChangePassword: true });
    }
    const account = this.currentAccount();
    if (account) {
      this.currentAccount.set({ ...account, mustChangePassword: true });
    }
  }

  hasPermission(code: PermissionCode): boolean {
    return this.permissions().has(code);
  }

  clear(): void {
    this.session.set(null);
    this.currentAccount.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
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
