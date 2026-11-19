import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ApiPaths } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { toProblem } from '../errors/error-messages';
import { AuthRoutes, RETURN_URL_PARAM } from './auth-routes';
import { SessionStore } from './session.store';

/**
 * Adds the bearer token to requests aimed at the API (never to third-party URLs) and reacts to
 * session errors: 401 ends the session, 403 PASSWORD_CHANGE_REQUIRED sends to the password change.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(AppConfigService);
  if (!config.isApiUrl(request.url)) {
    return next(request);
  }

  const session = inject(SessionStore);
  const router = inject(Router);
  const token = session.accessToken();
  const outgoing = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const isLogin = request.url === config.apiUrl(ApiPaths.login);
        const code = toProblem(error)?.code;
        // INVALID_CREDENTIALS on POST /auth/password means a wrong current password, not a dead session.
        if (error.status === 401 && token && !isLogin && code !== 'INVALID_CREDENTIALS') {
          session.clear();
          void router.navigate([AuthRoutes.login], {
            queryParams: { [RETURN_URL_PARAM]: router.url },
          });
        } else if (error.status === 403 && code === 'PASSWORD_CHANGE_REQUIRED') {
          session.markPasswordChangeRequired();
          void router.navigate([AuthRoutes.changePassword]);
        }
      }
      return throwError(() => error);
    }),
  );
};
