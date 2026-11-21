import type { UrlTree } from '@angular/router';
import { Router } from '@angular/router';
import type { TwoFactorStep } from '../api/api-types';
import { AuthRoutes, RETURN_URL_PARAM } from './auth-routes';
import type { LoginOutcome } from './auth.service';
import { landingUrl, safeReturnUrl } from './navigation';

/**
 * Where to go after a login step: the next selection page (keeping the return URL), the
 * password change, the return URL or the landing page. Null when the session could not load
 * GET /me (the page shows a generic error).
 */
export function nextUrlAfterLogin(
  router: Router,
  outcome: LoginOutcome,
  returnUrl: string | null | undefined,
): UrlTree | null {
  const safe = safeReturnUrl(returnUrl);
  const queryParams = safe ? { [RETURN_URL_PARAM]: safe } : {};
  switch (outcome.status) {
    case 'CLINIC_SELECTION_REQUIRED':
      return router.createUrlTree([AuthRoutes.loginClinic], { queryParams });
    case 'BRANCH_SELECTION_REQUIRED':
      return router.createUrlTree([AuthRoutes.loginBranch], { queryParams });
    case 'TWO_FACTOR_REQUIRED':
      return router.createUrlTree([twoFactorRoute(outcome.step)], { queryParams });
    case 'AUTHENTICATED': {
      const account = outcome.account;
      if (!account) {
        return null;
      }
      const target = account.mustChangePassword
        ? landingUrl(account)
        : (safe ?? landingUrl(account));
      return router.parseUrl(target);
    }
  }
}

/** Page of a pending second factor step: enrollment or verification (ADR-019). */
export function twoFactorRoute(step: TwoFactorStep): string {
  return step === 'ENROLLMENT_REQUIRED' ? AuthRoutes.twoFactorEnroll : AuthRoutes.twoFactorVerify;
}

/** Reads the one-time token of an email link: `#t=<token>` (ADR-014). */
export function fragmentToken(fragment: string | null | undefined): string | null {
  if (!fragment) {
    return null;
  }
  const token = new URLSearchParams(fragment.replace(/^#/, '')).get('t')?.trim();
  return token ? token : null;
}
