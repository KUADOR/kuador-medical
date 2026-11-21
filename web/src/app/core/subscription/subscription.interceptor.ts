import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { OwnerPaths } from '../api/api-types';
import { toProblem } from '../errors/error-messages';
import { SubscriptionService } from './subscription.service';

/**
 * A write rejected with 403 TENANT_READ_ONLY means the subscription changed (e.g. it was
 * suspended while the session was open): reload it so the shell shows the read-only notice. The
 * page still shows the Spanish message of the error.
 */
export const subscriptionInterceptor: HttpInterceptorFn = (request, next) => {
  const subscriptions = inject(SubscriptionService);
  return next(request).pipe(
    catchError((error: unknown) => {
      const isSubscriptionRead = request.url.split('?', 1)[0].endsWith(OwnerPaths.subscription);
      if (!isSubscriptionRead && toProblem(error)?.code === 'TENANT_READ_ONLY') {
        void subscriptions.load();
      }
      return throwError(() => error);
    }),
  );
};
