import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import type { AccessPermission } from '../api/api-types';
import { AuthRoutes, RETURN_URL_PARAM } from './auth-routes';
import { AuthService, LoginStep } from './auth.service';
import { AccessRequirement, canAccess, landingUrl } from './navigation';
import { SessionStore } from './session.store';

/**
 * Requires a session whose password change is not pending, plus the permissions of the active
 * assignment and the plan module given. The API still enforces each operation (ADR-008); this
 * guard only shapes navigation.
 */
export function requireAccess(requirement: AccessRequirement = {}): CanActivateFn {
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
    if (!canAccess(account, requirement)) {
      return router.createUrlTree([AuthRoutes.forbidden]);
    }
    return true;
  };
}

/** Shorthand for pages that only need permissions (no plan module). */
export function requirePermissions(...permissions: AccessPermission[]): CanActivateFn {
  return requireAccess({ permissions });
}

/** Any authenticated account that already changed its password. */
export const authenticatedGuard: CanActivateFn = requireAccess();

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
    return router.parseUrl('/status');
  }
  const account = await session.ensureAccount();
  if (!account) {
    session.clear();
    return router.parseUrl('/status');
  }
  return router.parseUrl(landingUrl(account));
};

/** Login, signup and the email links are only for visitors: a session goes to its landing page. */
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

/**
 * Clinic and branch selection pages: only while that login step is pending (in memory, never
 * stored). A reload or an expired pre-auth token goes back to the login form.
 */
export function loginStepGuard(step: LoginStep['status']): CanActivateFn {
  return (): boolean | UrlTree => {
    const pending = inject(AuthService).pendingStep();
    return pending?.status === step ? true : inject(Router).createUrlTree([AuthRoutes.login]);
  };
}
