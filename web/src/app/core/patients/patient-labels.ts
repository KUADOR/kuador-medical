import type {
  ConsentGrantor,
  GuardianLink,
  LinkType,
  Patient,
  PatientAccountStatus,
} from '../api/api-types';
import { DOCUMENT_TYPES, SEX_OPTIONS } from '../catalogs/patient-catalogs';

/**
 * Age at which a patient becomes an adult (spec MEDK-12, FR-027). The API decides with the
 * clinic time zone; the web only uses it to show or hide the guardian section in advance.
 */
export const MAJORITY_AGE = 18;

export const LINK_TYPE_LABELS: Record<LinkType, string> = {
  LEGAL_GUARDIAN: 'Representante legal',
  EMERGENCY_CONTACT: 'Contacto de emergencia',
  DELEGATED_ACCESS: 'Acceso delegado',
};

export const END_REASON_LABELS: Record<NonNullable<GuardianLink['endReason']>, string> = {
  ADULTHOOD: 'Mayoría de edad del paciente',
  MANUAL: 'Terminado por la clínica',
};

export const ACCOUNT_STATUS_LABELS: Record<PatientAccountStatus, string> = {
  NONE: 'Sin cuenta',
  INVITED: 'Invitación enviada',
  PENDING_EMAIL_VERIFICATION: 'Pendiente de verificar el correo',
  ACTIVE: 'Activa',
  DISABLED: 'Desactivada',
};

export const ORIGIN_LABELS: Record<Patient['origin'], string> = {
  STAFF: 'Registrado por la clínica',
  SELF: 'Autorregistro',
};

/** Completes «Otorgado por …». */
export const CONSENT_GRANTOR_LABELS: Record<ConsentGrantor, string> = {
  PATIENT: 'el paciente',
  GUARDIAN: 'el representante',
};

export function documentTypeLabel(code: string): string {
  return DOCUMENT_TYPES.find((option) => option.code === code)?.label ?? code;
}

export function sexLabel(code: string): string {
  return SEX_OPTIONS.find((option) => option.code === code)?.label ?? code;
}

/** Today as yyyy-MM-dd in the browser time zone. */
export function localToday(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * True when a person born on `birthDate` (yyyy-MM-dd) is under MAJORITY_AGE on `today`
 * (yyyy-MM-dd). Null for an incomplete or invalid date.
 */
export function isMinor(birthDate: string, today: string = localToday()): boolean | null {
  const birth = parseDate(birthDate);
  const current = parseDate(today);
  if (!birth || !current) {
    return null;
  }
  let age = current.year - birth.year;
  if (current.month < birth.month || (current.month === birth.month && current.day < birth.day)) {
    age -= 1;
  }
  return age < MAJORITY_AGE;
}

/** Spanish date (e.g. «1 jun 2015») of a yyyy-MM-dd value, without time zone shifts. */
export function formatDate(value: string | null | undefined): string {
  const parts = value ? parseDate(value.slice(0, 10)) : null;
  if (!value || !parts) {
    return '—';
  }
  if (value.length > 10) {
    return DATE_TIME.format(new Date(value));
  }
  return DATE.format(new Date(Date.UTC(parts.year, parts.month - 1, parts.day)));
}

const DATE = new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeZone: 'UTC' });
const DATE_TIME = new Intl.DateTimeFormat('es', { dateStyle: 'medium' });

function parseDate(value: string): { year: number; month: number; day: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return null;
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  return month >= 1 && month <= 12 && day >= 1 && day <= 31 ? { year, month, day } : null;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function fullName(person: { firstNames: string; lastNames: string }): string {
  return `${person.firstNames} ${person.lastNames}`;
}
