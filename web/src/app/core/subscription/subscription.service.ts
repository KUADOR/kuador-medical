import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccessPermission, ClinicSubscription, OwnerPaths } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { SessionStore } from '../session/session.store';

/** Permission that lets an account read the subscription of its clinic (platform contract). */
export const SUBSCRIPTION_READ_PERMISSION = 'subscription.read' satisfies AccessPermission;

/**
 * Subscription of the current clinic (GET /tenant/subscription): status, access mode, plan,
 * limits and notices. Loaded for accounts with `subscription.read`; the shell shows its notice
 * and the API enforces the access mode again on every operation (ADR-017).
 */
@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);
  private readonly session = inject(SessionStore);

  private readonly state = signal<ClinicSubscription | null>(null);
  /** Bumped on every load or clear, so a stale answer never fills a newer state. */
  private generation = 0;

  readonly subscription = this.state.asReadonly();
  /** True while the clinic can only read and export (SUSPENDED, or CANCELLED in retention). */
  readonly readOnly = computed(() => {
    const mode = this.state()?.accessMode;
    return mode === 'READ_ONLY' || mode === 'RETENTION';
  });

  /** True when the current account may read the subscription of its clinic. */
  canRead(): boolean {
    return (
      this.session.account()?.scope === 'TENANT' &&
      this.session.hasPermission(SUBSCRIPTION_READ_PERMISSION)
    );
  }

  /**
   * Loads (or reloads) the subscription when the account may read it; otherwise forgets it.
   * Errors keep the last known state: the notice is a help, the API is the one that enforces.
   */
  async load(): Promise<ClinicSubscription | null> {
    const generation = ++this.generation;
    if (!this.canRead()) {
      this.state.set(null);
      return null;
    }
    try {
      const subscription = await firstValueFrom(
        this.http.get<ClinicSubscription>(this.config.apiUrl(OwnerPaths.subscription)),
      );
      if (generation === this.generation) {
        this.state.set(subscription);
      }
      return subscription;
    } catch {
      return this.state();
    }
  }

  clear(): void {
    this.generation++;
    this.state.set(null);
  }
}
