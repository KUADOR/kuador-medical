import { HttpErrorResponse } from '@angular/common/http';
import type { ErrorCode, FieldError, Problem } from '../api/api-types';

/**
 * User-facing message (Spanish) for every stable error code of the contract.
 * Typed as Record<ErrorCode, string>: a new code in the contract breaks the build until it is mapped.
 */
export const ERROR_MESSAGES: Record<ErrorCode, string> = {
  VALIDATION: 'Revisa los datos ingresados: hay campos incompletos o con un formato no válido.',
  EMAIL_TAKEN: 'Ya existe una cuenta con ese correo.',
  INVALID_CREDENTIALS: 'El correo o la contraseña no son correctos.',
  WEAK_PASSWORD:
    'La contraseña nueva debe tener al menos 12 caracteres y ser distinta de la actual.',
  UNAUTHENTICATED: 'Tu sesión terminó. Vuelve a ingresar.',
  FORBIDDEN: 'No tienes permiso para realizar esta acción.',
  PASSWORD_CHANGE_REQUIRED: 'Debes cambiar tu contraseña antes de continuar.',
  NOT_FOUND: 'No encontramos lo que buscas.',
  ROLE_NAME_TAKEN: 'Ya existe un rol con ese nombre en tu clínica.',
  VERSION_CONFLICT:
    'Otra persona modificó estos datos mientras los editabas. Recarga para ver los cambios.',
  SYSTEM_ROLE_IMMUTABLE:
    'El rol OWNER está protegido: no se puede editar ni eliminar. El rol ADMIN no se puede eliminar.',
  ROLE_IN_USE: 'No se puede eliminar el rol porque está asignado a personas de tu clínica.',
  INTERNAL_ERROR: 'Ocurrió un error inesperado. Intenta de nuevo en unos minutos.',
  TOO_MANY_ATTEMPTS:
    'Hiciste demasiados intentos. Por seguridad el acceso quedó bloqueado por unos minutos; intenta de nuevo más tarde.',
  INVALID_SESSION_TOKEN:
    'Tu inicio de sesión venció. Vuelve a ingresar con tu correo y contraseña.',
  ONE_TIME_TOKEN_INVALID:
    'El enlace no es válido: venció o ya fue usado. Pide uno nuevo para continuar.',
  BRANCH_NOT_ASSIGNED: 'No tienes una asignación en esa sucursal.',
  PRIVILEGE_ESCALATION: 'No puedes otorgar permisos o roles que tú no tienes.',
  SELF_MODIFICATION_FORBIDDEN: 'No puedes cambiar tus propios roles ni tu propio acceso.',
  LAST_OWNER: 'La clínica debe conservar al menos una persona con el rol OWNER.',
  PLAN_LIMIT_REACHED: 'Tu clínica alcanzó el límite de usuarios de su plan.',
  IDEMPOTENCY_KEY_REUSED:
    'Esta solicitud ya se envió con otros datos. Recarga la página e intenta de nuevo.',
  RESOURCE_ID_TAKEN: 'Esta solicitud ya se envió. Recarga la página e intenta de nuevo.',
  ACCOUNT_ALREADY_ACTIVE: 'La cuenta ya está activa. Ingresa con tu correo y contraseña.',
  PATIENT_DUPLICATE: 'Ya existe un paciente con ese tipo y número de documento en la clínica.',
  MINOR_CANNOT_HAVE_ACCOUNT: 'Un paciente menor de edad no puede tener una cuenta propia.',
  ADULT_AUTHORIZATION_REQUIRED: 'Se necesita la autorización explícita del paciente adulto.',
  LAST_LEGAL_GUARDIAN: 'Un paciente menor de edad debe conservar al menos un representante legal.',
  CONTACT_CHANNEL_UNAVAILABLE:
    'El paciente no tiene un correo o teléfono registrado para enviarle la invitación.',
};

/** Messages for field-level validation codes (Problem.errors[].code). */
export const FIELD_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  REQUIRED: 'Este campo es obligatorio.',
  TOO_LONG: 'El texto es demasiado largo.',
  TOO_SHORT: 'El texto es demasiado corto.',
  INVALID_FORMAT: 'El formato no es válido.',
  UNKNOWN_PERMISSION: 'El permiso no existe en el catálogo del sistema.',
  MINOR_NOT_ALLOWED: 'Solo las personas mayores de edad pueden registrarse por su cuenta.',
  DUPLICATE: 'Este valor ya está registrado.',
  UNKNOWN_BRANCH: 'La sucursal no existe en tu clínica.',
  UNKNOWN_ROLE: 'El rol no existe en tu clínica.',
};

export const NETWORK_ERROR_MESSAGE =
  'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.';
export const GENERIC_ERROR_MESSAGE = ERROR_MESSAGES.INTERNAL_ERROR;
export const GENERIC_FIELD_ERROR_MESSAGE = 'El valor no es válido.';

const KNOWN_CODES = new Set<string>(Object.keys(ERROR_MESSAGES));

export function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && KNOWN_CODES.has(value);
}

/** Returns the Problem Details body of an HTTP error, or null when the body is not one. */
export function toProblem(error: unknown): Problem | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = error.error;
  if (body && typeof body === 'object' && isErrorCode((body as { code?: unknown }).code)) {
    return body as Problem;
  }
  return null;
}

/** Translates any error into a message for people. Never exposes server details. */
export function errorMessage(error: unknown): string {
  const problem = toProblem(error);
  if (problem) {
    return ERROR_MESSAGES[problem.code];
  }
  if (isServerUnreachable(error)) {
    return NETWORK_ERROR_MESSAGE;
  }
  return GENERIC_ERROR_MESSAGE;
}

/**
 * Gateway statuses a proxy answers when it cannot reach the api: the dev server proxy sends an
 * empty 502 and nginx 502/504 pages. The api itself always answers with a Problem Details code.
 */
const GATEWAY_STATUSES = new Set([502, 503, 504]);

/** True when the request never got an answer from the api (network failure or gateway error). */
export function isServerUnreachable(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    (error.status === 0 || (GATEWAY_STATUSES.has(error.status) && toProblem(error) === null))
  );
}

export function fieldErrorMessage(fieldError: FieldError): string {
  return FIELD_ERROR_MESSAGES[fieldError.code] ?? GENERIC_FIELD_ERROR_MESSAGE;
}
