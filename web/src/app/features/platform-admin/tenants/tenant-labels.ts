import type {
  AccessMode,
  BillingPeriod,
  ClinicType,
  InvitationStatus,
  LimitCode,
  ModuleCode,
  PlanChangeKind,
  Plan,
  Subscription,
  SubscriptionStatus,
  TransitionCause,
} from '../../../core/api/api-types';
import type { KdBadgeTone } from '../../../design-system';

/** Day counts and dates of the platform use the Ecuador time zone (contract § Dates). */
export const PLATFORM_TIME_ZONE = 'America/Guayaquil';

/** Spanish names of the subscription statuses (TRIAL, ACTIVA, VENCIDA, SUSPENDIDA, CANCELADA). */
export const SUBSCRIPTION_STATUS_LABELS: Record<SubscriptionStatus, string> = {
  TRIAL: 'Prueba',
  ACTIVE: 'Activa',
  PAST_DUE: 'Vencida',
  SUSPENDED: 'Suspendida',
  CANCELLED: 'Cancelada',
};

/** Badge tone of each status; the label always says the status (R-08). */
export const SUBSCRIPTION_STATUS_TONES: Record<SubscriptionStatus, KdBadgeTone> = {
  TRIAL: 'info',
  ACTIVE: 'ok',
  PAST_DUE: 'warning',
  SUSPENDED: 'error',
  CANCELLED: 'neutral',
};

export const SUBSCRIPTION_STATUSES = Object.keys(
  SUBSCRIPTION_STATUS_LABELS,
) as SubscriptionStatus[];

export const ACCESS_MODE_LABELS: Record<AccessMode, string> = {
  FULL: 'Acceso completo según el plan',
  READ_ONLY: 'Solo lectura y exportación',
  RETENTION: 'Retención: solo lectura y exportación para el OWNER',
  NONE: 'Sin acceso: datos purgados',
};

export const CLINIC_TYPE_LABELS: Record<ClinicType, string> = {
  MEDICAL: 'Médica',
  DENTAL: 'Dental',
  MIXED: 'Mixta',
};

export const CLINIC_TYPES = Object.keys(CLINIC_TYPE_LABELS) as ClinicType[];

/** Countries offered at onboarding: the contract offers only Ecuador today. */
export const COUNTRY_LABELS: Readonly<Record<string, string>> = { EC: 'Ecuador' };

export const BILLING_PERIOD_LABELS: Record<BillingPeriod, string> = {
  MONTHLY: 'Mensual',
  ANNUAL: 'Anual',
};

export const BILLING_PERIODS = Object.keys(BILLING_PERIOD_LABELS) as BillingPeriod[];

export const INVITATION_STATUS_LABELS: Record<InvitationStatus, string> = {
  PENDING: 'Pendiente',
  ACCEPTED: 'Aceptada',
  EXPIRED: 'Vencida',
  REVOKED: 'Reemplazada por otra',
};

export const TRANSITION_CAUSE_LABELS: Record<TransitionCause, string> = {
  MANUAL: 'Manual',
  PAYMENT_RECEIVED: 'Pago recibido',
  PAYMENT_FAILED: 'Pago fallido',
  SCHEDULER: 'Automática por plazo',
};

export const PLAN_CHANGE_KIND_LABELS: Record<PlanChangeKind, string> = {
  UPGRADE: 'Subida de plan: aplica de inmediato con prorrateo',
  DOWNGRADE: 'Bajada de plan: aplica en la próxima renovación, sin reembolso',
  PERIOD_CHANGE: 'Cambio de periodicidad: aplica en la próxima renovación',
};

export const LIMIT_LABELS: Record<LimitCode, string> = {
  MAX_BRANCHES: 'Sedes',
  MAX_USERS: 'Usuarios',
  MAX_PATIENTS: 'Pacientes',
  MAX_APPOINTMENTS: 'Citas',
  MAX_STORAGE_MB: 'Almacenamiento (MB)',
};

export const MODULE_LABELS: Record<ModuleCode, string> = {
  AGENDA: 'Agenda',
  PATIENTS: 'Pacientes',
  CLINICAL_RECORDS: 'Historia clínica',
  CONSULTATION: 'Consulta',
  ODONTOGRAM: 'Odontograma',
  USERS: 'Usuarios',
  PHARMACY: 'Farmacia',
  WAREHOUSE: 'Bodega',
  CASH_REGISTER: 'Caja',
  COLLECTIONS: 'Cobranzas',
  NOTIFICATIONS: 'Notificaciones',
  E_INVOICING: 'Facturación electrónica SRI',
  PUBLIC_API: 'API',
  ADVANCED_REPORTS: 'Reportes avanzados',
};

/** Field limits of CreateTenantRequest and the reason of the audit trail. */
export const TENANT_LIMITS = {
  legalName: 200,
  tradeName: 200,
  city: 120,
  address: 300,
  phoneMin: 7,
  phoneMax: 20,
  email: 254,
  msp: 40,
  ownerName: 160,
  reason: 500,
} as const;

/** RUC: 13 digits (contract `Ruc`, plan D8). */
export const RUC_PATTERN = /^\d{13}$/;

/** Statuses that allow a plan change (contract: TRIAL, ACTIVE and PAST_DUE). */
const PLAN_CHANGE_STATUSES: readonly SubscriptionStatus[] = ['TRIAL', 'ACTIVE', 'PAST_DUE'];

export function canChangePlan(subscription: Subscription): boolean {
  return !subscription.purged && PLAN_CHANGE_STATUSES.includes(subscription.status);
}

/**
 * Manual targets offered now. They come from the api (`allowedTargets`); the reactivation of a
 * CANCELLED subscription is offered only while the api says it is reactivable (plan D12).
 */
export function transitionTargets(subscription: Subscription): SubscriptionStatus[] {
  if (subscription.purged) {
    return [];
  }
  return subscription.allowedTargets.filter(
    (target) =>
      target !== subscription.status &&
      !(subscription.status === 'CANCELLED' && target === 'ACTIVE' && !subscription.reactivable),
  );
}

/** Spanish name of the action that moves the subscription to `target`. */
export function transitionActionLabel(
  from: SubscriptionStatus,
  target: SubscriptionStatus,
): string {
  switch (target) {
    case 'ACTIVE':
      if (from === 'CANCELLED') {
        return 'Reactivar la clínica cancelada';
      }
      return from === 'SUSPENDED' ? 'Reactivar' : 'Activar';
    case 'SUSPENDED':
      return 'Suspender';
    case 'CANCELLED':
      return 'Cancelar la suscripción';
    default:
      return `Pasar a ${SUBSCRIPTION_STATUS_LABELS[target]}`;
  }
}

/** Purge needs a CANCELLED clinic whose retention ended and that is not purged (ADR-018). */
export function purgeAvailable(subscription: Subscription, now: Date = new Date()): boolean {
  if (subscription.purged || subscription.status !== 'CANCELLED' || !subscription.retentionEndsAt) {
    return false;
  }
  return new Date(subscription.retentionEndsAt).getTime() <= now.getTime();
}

/** Display name of a clinic: trade name when it has one, else the legal name. */
export function clinicName(tenant: { legalName: string; tradeName?: string | null }): string {
  return tenant.tradeName?.trim() || tenant.legalName;
}

/** "Standard · Mensual 49,00 USD · Anual 490,00 USD" (prices without VAT). */
export function planOptionLabel(plan: Plan): string {
  if (plan.trial) {
    return `${plan.name} · prueba sin costo`;
  }
  const prices = plan.currentPrices
    .map(
      (price) =>
        `${BILLING_PERIOD_LABELS[price.billingPeriod]} ${formatMoney(price.amount, price.currency)}`,
    )
    .join(' · ');
  return prices ? `${plan.name} · ${prices}` : plan.name;
}

/** Amount of the contract (decimal string, without VAT) for people: "49,00 USD". */
export function formatMoney(amount: string | null | undefined, currency: string): string | null {
  if (amount === null || amount === undefined) {
    return null;
  }
  const value = Number(amount);
  if (Number.isNaN(value)) {
    return null;
  }
  const number = new Intl.NumberFormat('es', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
  return `${number} ${currency}`;
}

/** Instant of the contract as a date in Spanish, in the Ecuador time zone. */
export function formatDate(value: string | null | undefined): string | null {
  return format(value, { dateStyle: 'long', timeZone: PLATFORM_TIME_ZONE });
}

/** Instant of the contract as date and time in Spanish, in the Ecuador time zone. */
export function formatDateTime(value: string | null | undefined): string | null {
  return format(value, { dateStyle: 'long', timeStyle: 'short', timeZone: PLATFORM_TIME_ZONE });
}

/** Calendar day of the contract (`yyyy-MM-dd`, e.g. the price lock) in Spanish. */
export function formatDay(value: string | null | undefined): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  return format(`${value}T12:00:00Z`, { dateStyle: 'long', timeZone: 'UTC' });
}

/** Today as `yyyy-MM-dd` in the Ecuador time zone (minimum of the price lock date). */
export function todayInEcuador(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: PLATFORM_TIME_ZONE }).format(now);
}

function format(
  value: string | null | undefined,
  options: Intl.DateTimeFormatOptions,
): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat('es', options).format(date);
}
