import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRouteSnapshot,
  NavigationEnd,
  Router,
  RouterLink,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthRoutes } from './core/session/auth-routes';
import { AuthService } from './core/session/auth.service';
import { AppRoutes, menuFor } from './core/session/navigation';
import { SessionStore } from './core/session/session.store';
import { KdButton, KdNav, type KdNavItem, KdShell } from './design-system';
import { BranchSwitcher } from './features/auth/branch-switcher/branch-switcher';

/** Public entry of the menu, for visitors and every account. */
const STATUS_ITEM: KdNavItem = {
  label: 'Estado del sistema',
  link: AppRoutes.status,
  icon: 'info',
};

@Component({
  selector: 'mk-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, KdShell, KdNav, KdButton, BranchSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly session = inject(SessionStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly links = { ...AppRoutes, login: AuthRoutes.login };
  /** Routes with `data: { bareLayout: true }` (the shell demo of the catalog) draw their own frame. */
  protected readonly bareLayout = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => hasBareLayout(this.router.routerState.snapshot.root)),
    ),
    { initialValue: false },
  );
  protected readonly isAuthenticated = this.session.isAuthenticated;
  /** "Name · Clinic" for clinic accounts, just the name for the platform account. */
  protected readonly accountLabel = computed(() => {
    const account = this.session.account();
    if (!account) {
      return null;
    }
    return account.clinic ? `${account.displayName} · ${account.clinic.name}` : account.displayName;
  });
  /**
   * Menu built from the permissions and plan modules of GET /me (menuFor, the existing filter:
   * the shell only renders it); the API enforces them again.
   */
  protected readonly navItems = computed<KdNavItem[]>(() => [
    ...menuFor(this.session.account()).map(({ label, link, icon }) => ({ label, link, icon })),
    STATUS_ITEM,
  ]);

  constructor() {
    // After a reload the tokens survive in sessionStorage: load GET /me to build the menu.
    void this.session.ensureAccount();
  }

  protected async logout(): Promise<void> {
    this.auth.logout();
    await this.router.navigateByUrl(AuthRoutes.login);
  }
}

function hasBareLayout(route: ActivatedRouteSnapshot): boolean {
  let current: ActivatedRouteSnapshot | null = route;
  while (current) {
    if (current.data['bareLayout'] === true) {
      return true;
    }
    current = current.firstChild;
  }
  return false;
}
