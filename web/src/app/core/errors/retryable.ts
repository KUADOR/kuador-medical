import { HttpErrorResponse } from '@angular/common/http';

/**
 * Network failures and server errors may have reached the API: an idempotent creation is retried
 * with the same key. Any other answer means the request was processed or rejected.
 */
export function isRetryable(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}
