import type {
  BillingPeriod,
  LimitInfo,
  PlanPrice,
  PlanStatus,
  PlatformSettings,
} from '../api/api-types';

/** Time zone of day counts and dates of the platform contract (§ Dates). */
export const PLATFORM_TIME_ZONE = 'America/Guayaquil';
/** America/Guayaquil has no daylight saving time: midnight there is 05:00 UTC. */
const GUAYAQUIL_MIDNIGHT_UTC = 'T05:00:00Z';

export const BILLING_PERIODS: readonly BillingPeriod[] = ['MONTHLY', 'ANNUAL'];

export const BILLING_PERIOD_LABELS: Record<BillingPeriod, string> = {
  MONTHLY: 'Mensual',
  ANNUAL: 'Anual',
};

export const PLAN_STATUS_LABELS: Record<PlanStatus, string> = {
  ACTIVE: 'Activo',
  ARCHIVED: 'Archivado',
};

/** Fields of the platform settings with their Spanish label, unit and contract bounds. */
export interface SettingField {
  key: Exclude<keyof PlatformSettings, 'version'>;
  label: string;
  hint: string;
  unit: 'días' | 'horas';
  min: number;
  max: number;
}

export const SETTING_FIELDS: readonly SettingField[] = [
  {
    key: 'trialDays',
    label: 'Duración de la prueba gratuita',
    hint: 'Días de prueba de una clínica nueva con el plan de prueba.',
    unit: 'días',
    min: 1,
    max: 365,
  },
  {
    key: 'graceDays',
    label: 'Días de gracia tras el vencimiento',
    hint: 'Días con acceso completo y avisos antes de suspender una suscripción vencida.',
    unit: 'días',
    min: 0,
    max: 90,
  },
  {
    key: 'suspensionDaysBeforeCancel',
    label: 'Días suspendida antes de cancelar',
    hint: 'Días en solo lectura antes de que la suscripción pase a cancelada.',
    unit: 'días',
    min: 1,
    max: 365,
  },
  {
    key: 'retentionDays',
    label: 'Retención tras la cancelación',
    hint: 'Días de solo lectura y exportación antes de que se pueda borrar la clínica.',
    unit: 'días',
    min: 1,
    max: 3650,
  },
  {
    key: 'ownerInvitationTtlHours',
    label: 'Vigencia de la invitación al OWNER',
    hint: 'Horas que dura el enlace de un solo uso de la invitación.',
    unit: 'horas',
    min: 1,
    max: 720,
  },
  {
    key: 'priceChangeNoticeDays',
    label: 'Aviso previo de cambio de tarifa',
    hint: 'Días mínimos entre el aviso al OWNER y la renovación con el precio nuevo.',
    unit: 'días',
    min: 0,
    max: 365,
  },
];

/** Amount as the user types it: digits, optionally with up to two decimals (dot or comma). */
export const AMOUNT_PATTERN = /^\d{1,10}([.,]\d{1,2})?$/;

/** Normalizes a typed amount to the contract `Money` format (`"49.00"`), or null if invalid. */
export function toMoney(value: string): string | null {
  const trimmed = value.trim();
  if (!AMOUNT_PATTERN.test(trimmed)) {
    return null;
  }
  const [units, decimals = ''] = trimmed.replace(',', '.').split('.');
  return `${String(Number(units))}.${decimals.padEnd(2, '0')}`;
}

/** «USD 49,00» with the Spanish (Ecuador) number format. Amounts never include VAT. */
export function formatMoney(amount: string, currency = 'USD'): string {
  const value = Number(amount);
  const formatted = new Intl.NumberFormat('es-EC', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
  return `${currency} ${formatted}`;
}

/** Date of an instant in the platform time zone, e.g. «1 mar 2030». */
export function formatDay(instant: string): string {
  return new Intl.DateTimeFormat('es-EC', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: PLATFORM_TIME_ZONE,
  }).format(new Date(instant));
}

/** Today (yyyy-MM-dd) in the platform time zone. */
export function platformToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: PLATFORM_TIME_ZONE,
  }).format(now);
}

/**
 * Instant from which a new price applies, for a day (yyyy-MM-dd) chosen in the platform time
 * zone: midnight of that day, or the next whole minute when the day is today (the contract
 * rejects an `effectiveFrom` in the past).
 */
export function effectiveFromInstant(day: string, now: Date = new Date()): string {
  if (day === platformToday(now)) {
    const next = new Date(now.getTime());
    next.setUTCSeconds(0, 0);
    next.setUTCMinutes(next.getUTCMinutes() + 1);
    return next.toISOString().replace('.000Z', 'Z');
  }
  return `${day}${GUAYAQUIL_MIDNIGHT_UTC}`;
}

/** «3», «10.240 MB» or «Sin límite». */
export function formatLimit(value: number | null | undefined, unit: LimitInfo['unit']): string {
  if (value === null || value === undefined) {
    return 'Sin límite';
  }
  const formatted = new Intl.NumberFormat('es-EC').format(value);
  return unit === 'MEGABYTES' ? `${formatted} MB` : formatted;
}

/** Current price of a plan for one billing period, if it has one. */
export function currentPrice(
  prices: readonly PlanPrice[],
  period: BillingPeriod,
): PlanPrice | undefined {
  return prices.find((price) => price.billingPeriod === period);
}

/**
 * Saving of the annual price against twelve monthly payments, in months (e.g. 2 for
 * «2 meses gratis»), rounded to one decimal; null when there is no saving or a price is missing.
 */
export function annualSavingMonths(monthly: string | null, annual: string | null): number | null {
  const month = monthly === null ? NaN : Number(monthly);
  const year = annual === null ? NaN : Number(annual);
  if (!(month > 0) || !(year > 0) || year >= month * 12) {
    return null;
  }
  return Math.round(((month * 12 - year) / month) * 10) / 10;
}
