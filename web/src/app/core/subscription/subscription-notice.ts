import type { ClinicSubscription, NoticeKind } from '../api/api-types';
import type { KdAlertTone } from '../../design-system';

/** Notice of the subscription status shown to the clinic (spec FR-018, FR-021). */
export interface SubscriptionNoticeView {
  tone: KdAlertTone;
  heading: string;
  message: string;
  /** The clinic can only read and export: offer the data export. */
  readOnly: boolean;
}

const DATE_FORMAT = new Intl.DateTimeFormat('es-EC', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'America/Guayaquil',
});

/** Day of an instant in the time zone of the contract (America/Guayaquil), in Spanish. */
export function formatDay(instant: string | null | undefined): string | null {
  if (!instant) {
    return null;
  }
  const date = new Date(instant);
  return Number.isNaN(date.getTime()) ? null : DATE_FORMAT.format(date);
}

function noticeDate(subscription: ClinicSubscription, kind: NoticeKind): string | null {
  const notice = subscription.notices.find((item) => item.kind === kind);
  return formatDay(notice?.effectiveAt);
}

/**
 * Notice for the statuses that change what the clinic can do: PAST_DUE (grace with full access),
 * SUSPENDED (read and export only) and CANCELLED (retention: read and export only). Null for
 * TRIAL and ACTIVE.
 */
export function subscriptionNotice(
  subscription: ClinicSubscription | null,
): SubscriptionNoticeView | null {
  if (!subscription) {
    return null;
  }
  switch (subscription.status) {
    case 'PAST_DUE': {
      const until =
        formatDay(subscription.graceEndsAt) ?? noticeDate(subscription, 'PAYMENT_OVERDUE');
      return {
        tone: 'warning',
        heading: 'Suscripción vencida',
        message: until
          ? `El pago de la suscripción está pendiente. Mantienes el acceso completo hasta el ${until}; después la clínica quedará en modo solo lectura.`
          : 'El pago de la suscripción está pendiente. Mantienes el acceso completo durante el período de gracia; después la clínica quedará en modo solo lectura.',
        readOnly: false,
      };
    }
    case 'SUSPENDED':
      return {
        tone: 'error',
        heading: 'Suscripción suspendida',
        message:
          'La clínica está en modo solo lectura: puedes consultar y exportar los datos, pero no crear ni editar. Comunícate con Kuador para reactivarla.',
        readOnly: true,
      };
    case 'CANCELLED': {
      const until =
        formatDay(subscription.retentionEndsAt) ?? noticeDate(subscription, 'CANCELLED');
      return {
        tone: 'error',
        heading: 'Suscripción cancelada',
        message: until
          ? `La clínica está en período de retención hasta el ${until}: solo puedes consultar y exportar los datos. Exporta lo que necesites antes de esa fecha.`
          : 'La clínica está en período de retención: solo puedes consultar y exportar los datos. Exporta lo que necesites antes de que termine.',
        readOnly: true,
      };
    }
    default:
      return null;
  }
}
