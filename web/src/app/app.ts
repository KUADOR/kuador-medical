import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthRoutes } from './core/session/auth-routes';
import { AuthService } from './core/session/auth.service';
import { AppRoutes, menuFor } from './core/session/navigation';
import { SessionStore } from './core/session/session.store';
import { BranchSwitcher } from './features/auth/branch-switcher/branch-switcher';

@Component({
  selector: 'mk-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BranchSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly session = inject(SessionStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly links = { ...AppRoutes, login: AuthRoutes.login };
  protected readonly isAuthenticated = this.session.isAuthenticated;
  /** "Name · Clinic" for clinic accounts, just the name for the platform account. */
  protected readonly accountLabel = computed(() => {
    const account = this.session.account();
    if (!account) {
      return null;
    }
    return account.clinic ? `${account.displayName} · ${account.clinic.name}` : account.displayName;
  });
  /** Menu built from the permissions and plan modules of GET /me; the API enforces them again. */
  protected readonly menu = computed(() => menuFor(this.session.account()));

  constructor() {
    // After a reload the tokens survive in sessionStorage: load GET /me to build the menu.
    void this.session.ensureAccount();
  }

  protected async logout(): Promise<void> {
    this.auth.logout();
    await this.router.navigateByUrl(AuthRoutes.login);
  }
}
