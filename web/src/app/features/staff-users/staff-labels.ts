import type { AccountStatus, StaffUser } from '../../core/api/api-types';

/** Spanish names of the account statuses of the contract. */
export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  INVITED: 'Invitación pendiente',
  PENDING_EMAIL_VERIFICATION: 'Verificación de correo pendiente',
  ACTIVE: 'Activo',
  DISABLED: 'Deshabilitado',
};

export const ACCOUNT_STATUSES = Object.keys(ACCOUNT_STATUS_LABELS) as AccountStatus[];

export function fullName(user: Pick<StaffUser, 'firstNames' | 'lastNames'>): string {
  return `${user.firstNames} ${user.lastNames}`.trim();
}

/** Date and time in Spanish for people (e.g. the invitation expiry). */
export function formatDateTime(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat('es', { dateStyle: 'long', timeStyle: 'short' }).format(date);
}

/** Field limits of the contract (CreateStaffUserRequest, UpdateStaffUserRequest). */
export const STAFF_LIMITS = {
  name: 120,
  email: 254,
  jobTitle: 120,
  professionalRegistration: 60,
} as const;
