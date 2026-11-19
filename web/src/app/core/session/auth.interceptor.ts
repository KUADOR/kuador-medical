import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, of, switchMap, throwError } from 'rxjs';
import { PUBLIC_SESSION_PATHS } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { toProblem } from '../errors/error-messages';
import { AuthRoutes, RETURN_URL_PARAM } from './auth-routes';
import { SessionStore } from './session.store';
import { TokenRefresher } from './token-refresher';

/**
 * Adds the bearer token to requests aimed at the API (never to third-party URLs nor to the
 * public auth operations) and keeps the session alive:
 * - an expired access token is refreshed before the request is sent;
 * - a 401 is answered with one refresh and one retry; if that fails the session ends;
 * - 403 PASSWORD_CHANGE_REQUIRED sends to the password change.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(AppConfigService);
  if (!config.isApiUrl(request.url) || isPublic(request.url, config)) {
    return next(request);
  }

  const session = inject(SessionStore);
  const refresher = inject(TokenRefresher);
  const router = inject(Router);
  const hadSession = session.isAuthenticated();

  const endSession = () => {
    session.clear();
    void router.navigate([AuthRoutes.login], {
      queryParams: { [RETURN_URL_PARAM]: router.url },
    });
  };

  const reactToForbidden = (error: unknown) => {
    if (
      error instanceof HttpErrorResponse &&
      error.status === 403 &&
      toProblem(error)?.code === 'PASSWORD_CHANGE_REQUIRED'
    ) {
      session.markPasswordChangeRequired();
      void router.navigate([AuthRoutes.changePassword]);
    }
  };

  // A valid token is used right away; only an expired one waits for the refresh.
  const current = session.accessToken();
  const token$: Observable<string | null> = !hadSession
    ? of(null)
    : current
      ? of(current)
      : from(refresher.validAccessToken());

  return token$.pipe(
    switchMap((token) => {
      if (hadSession && !token && !session.isAuthenticated()) {
        // The refresh token was rejected while getting a token for this request.
        endSession();
      }
      return send(request, next, token).pipe(
        catchError((error: unknown) => {
          if (isSessionRejected(error) && token) {
            return retryAfterRefresh(request, next, refresher, token, endSession);
          }
          return throwError(() => error);
        }),
      );
    }),
    // Also covers the retried request after a refresh.
    catchError((error: unknown) => {
      reactToForbidden(error);
      return throwError(() => error);
    }),
  );
};

function send(
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
  token: string | null,
): Observable<HttpEvent<unknown>> {
  return next(
    token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request,
  );
}

function retryAfterRefresh(
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
  refresher: TokenRefresher,
  rejected: string,
  endSession: () => void,
): Observable<HttpEvent<unknown>> {
  return from(refresher.refresh(rejected)).pipe(
    switchMap((fresh) => {
      if (!fresh) {
        endSession();
        return throwError(() => sessionEnded(request));
      }
      return send(request, next, fresh).pipe(
        catchError((error: unknown) => {
          if (isSessionRejected(error)) {
            endSession();
          }
          return throwError(() => error);
        }),
      );
    }),
  );
}

/**
 * 401 that means "this access token is not accepted". INVALID_CREDENTIALS on POST /auth/password
 * is a wrong current password, not a dead session.
 */
function isSessionRejected(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === 401 &&
    toProblem(error)?.code !== 'INVALID_CREDENTIALS'
  );
}

function sessionEnded(request: HttpRequest<unknown>): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 401,
    statusText: 'Unauthorized',
    url: request.url,
    error: { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHENTICATED' },
  });
}

function isPublic(url: string, config: AppConfigService): boolean {
  const path = url.split('?', 1)[0];
  return (
    PUBLIC_SESSION_PATHS.some((publicPath) => path === config.apiUrl(publicPath)) ||
    path.startsWith(config.apiUrl('/public/'))
  );
}
