import {
  annualSavingMonths,
  effectiveFromInstant,
  formatLimit,
  formatMoney,
  platformToday,
  toMoney,
} from './plan-labels';

describe('plan labels', () => {
  it('normalizes typed amounts to the contract Money format', () => {
    expect(toMoney('49')).toBe('49.00');
    expect(toMoney('49.5')).toBe('49.50');
    expect(toMoney('49,90')).toBe('49.90');
    expect(toMoney(' 007.10 ')).toBe('7.10');
    expect(toMoney('49.999')).toBeNull();
    expect(toMoney('-1')).toBeNull();
    expect(toMoney('abc')).toBeNull();
    expect(toMoney('')).toBeNull();
  });

  it('shows money with its currency and two decimals', () => {
    expect(formatMoney('490.00')).toMatch(/^USD 490[,.]00$/);
    expect(formatMoney('1234.50', 'USD')).toMatch(/^USD 1[.\s ]?234[,.]50$/);
  });

  it('computes today in the Guayaquil time zone, not in UTC', () => {
    // 03:00 UTC on 2 March is still 1 March in Guayaquil (UTC-5).
    expect(platformToday(new Date('2030-03-02T03:00:00Z'))).toBe('2030-03-01');
    expect(platformToday(new Date('2030-03-02T06:00:00Z'))).toBe('2030-03-02');
  });

  it('starts a future price at midnight of Guayaquil and a price of today at the next minute', () => {
    const now = new Date('2030-03-01T15:20:31Z');
    expect(effectiveFromInstant('2030-03-10', now)).toBe('2030-03-10T05:00:00Z');
    expect(effectiveFromInstant('2030-03-01', now)).toBe('2030-03-01T15:21:00Z');
  });

  it('says «Sin límite» for null limits and adds MB to storage', () => {
    expect(formatLimit(null, 'COUNT')).toBe('Sin límite');
    expect(formatLimit(3, 'COUNT')).toBe('3');
    expect(formatLimit(500, 'MEGABYTES')).toBe('500 MB');
  });

  it('expresses the annual discount in free months', () => {
    expect(annualSavingMonths('49.00', '490.00')).toBe(2);
    expect(annualSavingMonths('49.00', '588.00')).toBeNull();
    expect(annualSavingMonths(null, '490.00')).toBeNull();
    expect(annualSavingMonths('0.00', '490.00')).toBeNull();
  });
});
