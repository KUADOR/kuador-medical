import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthRoutes } from './core/auth/auth-routes';
import { AuthService } from './core/auth/auth.service';
import { AppRoutes, menuFor } from './core/auth/navigation';
import { SessionStore } from './core/auth/session.store';

@Component({
  selector: 'mk-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly session = inject(SessionStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly links = { ...AppRoutes, login: AuthRoutes.login };
  protected readonly isAuthenticated = this.session.isAuthenticated;
  /** "Name · Clinic" for clinic staff, just the name for the platform account. */
  protected readonly accountLabel = computed(() => {
    const account = this.session.account();
    if (!account) {
      return null;
    }
    return account.tenant ? `${account.displayName} · ${account.tenant.name}` : account.displayName;
  });
  /** Menu built from the permissions of GET /me; the API enforces them again (ADR-008). */
  protected readonly menu = computed(() => menuFor(this.session.account()));

  constructor() {
    // After a reload the token survives in sessionStorage: load GET /me to build the menu.
    void this.session.ensureAccount();
  }

  protected async logout(): Promise<void> {
    this.auth.logout();
    await this.router.navigateByUrl(AuthRoutes.login);
  }
}
