import { HttpErrorResponse } from '@angular/common/http';
import type { ErrorCode } from '../api/api-types';
import {
  ERROR_MESSAGES,
  GENERIC_ERROR_MESSAGE,
  GENERIC_FIELD_ERROR_MESSAGE,
  NETWORK_ERROR_MESSAGE,
  errorMessage,
  fieldErrorMessage,
  toProblem,
} from './error-messages';

// Mirrors the ErrorCode enum of contracts/identity.openapi.yaml.
const CONTRACT_CODES: ErrorCode[] = [
  'VALIDATION',
  'EMAIL_TAKEN',
  'INVALID_CREDENTIALS',
  'WEAK_PASSWORD',
  'UNAUTHENTICATED',
  'FORBIDDEN',
  'PASSWORD_CHANGE_REQUIRED',
  'NOT_FOUND',
  'ROLE_NAME_TAKEN',
  'VERSION_CONFLICT',
  'SYSTEM_ROLE_IMMUTABLE',
  'ROLE_IN_USE',
  'INTERNAL_ERROR',
  'TOO_MANY_ATTEMPTS',
  'INVALID_SESSION_TOKEN',
  'ONE_TIME_TOKEN_INVALID',
  'BRANCH_NOT_ASSIGNED',
  'PRIVILEGE_ESCALATION',
  'SELF_MODIFICATION_FORBIDDEN',
  'LAST_OWNER',
  'PLAN_LIMIT_REACHED',
  'IDEMPOTENCY_KEY_REUSED',
  'RESOURCE_ID_TAKEN',
  'ACCOUNT_ALREADY_ACTIVE',
  'PATIENT_DUPLICATE',
  'MINOR_CANNOT_HAVE_ACCOUNT',
  'ADULT_AUTHORIZATION_REQUIRED',
  'LAST_LEGAL_GUARDIAN',
  'CONTACT_CHANNEL_UNAVAILABLE',
];

function problemResponse(status: number, code: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { type: 'about:blank', title: 'Error', status, code, detail: 'internal detail' },
  });
}

describe('error messages', () => {
  it('maps every contract error code to a non-empty message', () => {
    expect(Object.keys(ERROR_MESSAGES).sort()).toEqual([...CONTRACT_CODES].sort());
    for (const code of CONTRACT_CODES) {
      expect(ERROR_MESSAGES[code].trim().length).toBeGreaterThan(0);
    }
  });

  it('translates a Problem Details code and never shows the server detail', () => {
    const message = errorMessage(problemResponse(409, 'ROLE_NAME_TAKEN'));
    expect(message).toBe(ERROR_MESSAGES.ROLE_NAME_TAKEN);
    expect(message).not.toContain('internal detail');
  });

  it('uses the network message when the server is unreachable', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe(NETWORK_ERROR_MESSAGE);
  });

  it('uses the network message when a proxy or gateway cannot reach the api', () => {
    // The dev server proxy answers an empty 502; nginx answers 502/504 with an HTML page.
    for (const status of [502, 503, 504]) {
      expect(errorMessage(new HttpErrorResponse({ status, error: '' }))).toBe(
        NETWORK_ERROR_MESSAGE,
      );
      expect(errorMessage(new HttpErrorResponse({ status, error: '<html>' }))).toBe(
        NETWORK_ERROR_MESSAGE,
      );
    }
  });

  it('keeps the api message when a 503 carries a Problem Details code', () => {
    expect(errorMessage(problemResponse(503, 'INTERNAL_ERROR'))).toBe(
      ERROR_MESSAGES.INTERNAL_ERROR,
    );
  });

  it('falls back to the generic message for unknown codes or non-HTTP errors', () => {
    expect(errorMessage(problemResponse(500, 'SOMETHING_NEW'))).toBe(GENERIC_ERROR_MESSAGE);
    expect(errorMessage(new HttpErrorResponse({ status: 500, error: '<html>' }))).toBe(
      GENERIC_ERROR_MESSAGE,
    );
    expect(errorMessage(new Error('boom'))).toBe(GENERIC_ERROR_MESSAGE);
  });

  it('extracts the problem only when the body carries a known code', () => {
    expect(toProblem(problemResponse(404, 'NOT_FOUND'))?.code).toBe('NOT_FOUND');
    expect(toProblem(problemResponse(404, 'nope'))).toBeNull();
    expect(toProblem('text')).toBeNull();
  });

  it('translates field validation codes', () => {
    expect(fieldErrorMessage({ field: 'permissions[0]', code: 'UNKNOWN_PERMISSION' })).toBe(
      'El permiso no existe en el catálogo del sistema.',
    );
    expect(fieldErrorMessage({ field: 'name', code: 'WHATEVER' })).toBe(
      GENERIC_FIELD_ERROR_MESSAGE,
    );
  });
});
