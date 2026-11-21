import {
  ACTIVE_SUBSCRIPTION,
  CANCELLED_REACTIVABLE,
  CANCELLED_RETENTION_OVER,
  STANDARD_PLAN,
  SUSPENDED_SUBSCRIPTION,
  TRIAL_PLAN,
} from '../../../../testing/platform-tenant-fixtures';
import {
  canChangePlan,
  formatDay,
  formatMoney,
  planOptionLabel,
  purgeAvailable,
  todayInEcuador,
  transitionActionLabel,
  transitionTargets,
} from './tenant-labels';
import { subscriptionDates } from './tenant-detail.page';

describe('tenant labels and rules', () => {
  describe('transitionTargets', () => {
    it('offers the targets the api allows', () => {
      expect(transitionTargets(ACTIVE_SUBSCRIPTION)).toEqual(['SUSPENDED', 'CANCELLED']);
      expect(transitionTargets(SUSPENDED_SUBSCRIPTION)).toEqual(['ACTIVE', 'CANCELLED']);
    });

    it('offers the reactivation of a cancelled clinic only while it is reactivable (D12)', () => {
      expect(transitionTargets(CANCELLED_REACTIVABLE)).toEqual(['ACTIVE']);
      expect(transitionTargets(CANCELLED_RETENTION_OVER)).toEqual([]);
    });

    it('offers nothing for a purged clinic', () => {
      expect(transitionTargets({ ...CANCELLED_REACTIVABLE, purged: true })).toEqual([]);
    });

    it('never offers the current status', () => {
      expect(
        transitionTargets({ ...ACTIVE_SUBSCRIPTION, allowedTargets: ['ACTIVE', 'SUSPENDED'] }),
      ).toEqual(['SUSPENDED']);
    });
  });

  it('names each action by where it starts', () => {
    expect(transitionActionLabel('TRIAL', 'ACTIVE')).toBe('Activar');
    expect(transitionActionLabel('SUSPENDED', 'ACTIVE')).toBe('Reactivar');
    expect(transitionActionLabel('CANCELLED', 'ACTIVE')).toBe('Reactivar la clínica cancelada');
    expect(transitionActionLabel('ACTIVE', 'SUSPENDED')).toBe('Suspender');
    expect(transitionActionLabel('ACTIVE', 'CANCELLED')).toBe('Cancelar la suscripción');
  });

  it('allows plan changes only in TRIAL, ACTIVE and PAST_DUE', () => {
    expect(canChangePlan(ACTIVE_SUBSCRIPTION)).toBe(true);
    expect(canChangePlan({ ...ACTIVE_SUBSCRIPTION, status: 'TRIAL' })).toBe(true);
    expect(canChangePlan({ ...ACTIVE_SUBSCRIPTION, status: 'PAST_DUE' })).toBe(true);
    expect(canChangePlan(SUSPENDED_SUBSCRIPTION)).toBe(false);
    expect(canChangePlan(CANCELLED_REACTIVABLE)).toBe(false);
  });

  it('allows the purge only for a cancelled clinic whose retention ended', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    expect(purgeAvailable(CANCELLED_RETENTION_OVER, now)).toBe(true);
    expect(purgeAvailable(CANCELLED_REACTIVABLE, now)).toBe(false);
    expect(purgeAvailable({ ...CANCELLED_RETENTION_OVER, purged: true }, now)).toBe(false);
    expect(purgeAvailable({ ...CANCELLED_RETENTION_OVER, status: 'SUSPENDED' }, now)).toBe(false);
    expect(purgeAvailable({ ...CANCELLED_RETENTION_OVER, retentionEndsAt: null }, now)).toBe(false);
  });

  it('formats money without VAT with two decimals and its currency', () => {
    expect(formatMoney('49.00', 'USD')).toBe('49,00 USD');
    expect(formatMoney('1490.50', 'USD')).toMatch(/^1.?490,50 USD$/);
    expect(formatMoney(null, 'USD')).toBeNull();
  });

  it('describes a plan with its current prices, and the trial without cost', () => {
    expect(planOptionLabel(STANDARD_PLAN)).toBe('Standard · Mensual 49,00 USD · Anual 490,00 USD');
    expect(planOptionLabel(TRIAL_PLAN)).toBe('Prueba · prueba sin costo');
  });

  it('formats calendar days and today in Ecuador', () => {
    expect(formatDay('2030-12-31')).toBe('31 de diciembre de 2030');
    expect(formatDay('not a day')).toBeNull();
    // 03:00 UTC is still the previous day in Guayaquil (UTC-5).
    expect(todayInEcuador(new Date('2030-03-01T03:00:00Z'))).toBe('2030-02-28');
  });

  it('lists only the dates a subscription has, in the Ecuador time zone', () => {
    expect(subscriptionDates(ACTIVE_SUBSCRIPTION)).toEqual([
      { term: 'Inicio del período', value: '1 de enero de 2030' },
      { term: 'Fin del período', value: '1 de febrero de 2030' },
    ]);
  });
});
