import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import type { PermissionCode } from '../api/api-types';
import { AuthRoutes, RETURN_URL_PARAM } from './auth-routes';
import { AppRoutes, landingUrl } from './navigation';
import { SessionStore } from './session.store';

/**
 * Requires a session whose password change is not pending, plus every permission given.
 * The API still enforces each permission (ADR-008); this guard only shapes navigation.
 */
export function requirePermissions(...required: PermissionCode[]): CanActivateFn {
  return async (_route, state): Promise<boolean | UrlTree> => {
    const session = inject(SessionStore);
    const router = inject(Router);

    const account = await session.ensureAccount();
    if (!account) {
      session.clear();
      return router.createUrlTree([AuthRoutes.login], {
        queryParams: { [RETURN_URL_PARAM]: state.url },
      });
    }
    if (session.mustChangePassword()) {
      return router.createUrlTree([AuthRoutes.changePassword]);
    }
    if (!required.every((code) => session.hasPermission(code))) {
      return router.createUrlTree([AuthRoutes.forbidden]);
    }
    return true;
  };
}

/** Any authenticated account that already changed its password. */
export const authenticatedGuard: CanActivateFn = requirePermissions();

/** Only lets in sessions that must change the password; everyone else goes home or to login. */
export const pendingPasswordChangeGuard: CanActivateFn = async (): Promise<boolean | UrlTree> => {
  const session = inject(SessionStore);
  const router = inject(Router);

  const account = await session.ensureAccount();
  if (!account) {
    session.clear();
    return router.createUrlTree([AuthRoutes.login]);
  }
  return session.mustChangePassword() ? true : router.createUrlTree(['/']);
};

/** Root route: anonymous visitors see the system status, accounts go to their landing page. */
export const homeGuard: CanActivateFn = async (): Promise<UrlTree> => {
  const session = inject(SessionStore);
  const router = inject(Router);

  if (!session.isAuthenticated()) {
    return router.parseUrl(AppRoutes.status);
  }
  const account = await session.ensureAccount();
  if (!account) {
    session.clear();
    return router.parseUrl(AppRoutes.status);
  }
  return router.parseUrl(landingUrl(account));
};

/** Login and signup are only for visitors: an active session goes to its landing page. */
export const guestGuard: CanActivateFn = async (): Promise<boolean | UrlTree> => {
  const session = inject(SessionStore);
  const router = inject(Router);

  if (!session.isAuthenticated()) {
    return true;
  }
  const account = await session.ensureAccount();
  if (!account) {
    session.clear();
    return true;
  }
  return router.parseUrl(landingUrl(account));
};
