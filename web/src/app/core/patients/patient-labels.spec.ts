import { formatDate, isMinor, localToday } from './patient-labels';

describe('patient labels', () => {
  it('is a minor until the 18th birthday', () => {
    expect(isMinor('2015-06-01', '2033-05-31')).toBe(true);
    expect(isMinor('2015-06-01', '2033-06-01')).toBe(false);
    expect(isMinor('2015-06-01', '2033-07-01')).toBe(false);
    expect(isMinor('1990-03-15', '2026-11-19')).toBe(false);
    expect(isMinor('2010-12-31', '2028-12-30')).toBe(true);
  });

  it('has no answer for an incomplete date', () => {
    expect(isMinor('')).toBeNull();
    expect(isMinor('2015-13-01', '2026-01-01')).toBeNull();
  });

  it('formats dates in Spanish without shifting the day', () => {
    expect(formatDate('2015-06-01')).toMatch(/1 jun\.? 2015/);
    expect(formatDate(null)).toBe('—');
    expect(formatDate('2030-01-01T15:00:00Z')).toContain('2030');
  });

  it('builds today in the local calendar', () => {
    expect(localToday(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
