import { ACCOUNT, FULL_ACCOUNT, SUPER_ADMIN } from '../../../testing/fixtures';
import { landingUrl, menuFor, safeReturnUrl } from './navigation';

describe('navigation', () => {
  it('builds the menu from the permissions of GET /me', () => {
    expect(menuFor(FULL_ACCOUNT).map((item) => item.label)).toEqual(['Roles']);
    expect(menuFor(SUPER_ADMIN).map((item) => item.label)).toEqual(['Catálogo de permisos']);
    expect(menuFor({ ...ACCOUNT, permissions: [] })).toEqual([]);
    expect(menuFor(null)).toEqual([]);
  });

  it('shows no menu while the password change is pending', () => {
    expect(menuFor({ ...SUPER_ADMIN, mustChangePassword: true })).toEqual([]);
  });

  it('lands each account on its first page', () => {
    expect(landingUrl(null)).toBe('/login');
    expect(landingUrl({ ...FULL_ACCOUNT, mustChangePassword: true })).toBe('/change-password');
    expect(landingUrl(FULL_ACCOUNT)).toBe('/roles');
    expect(landingUrl(SUPER_ADMIN)).toBe('/platform/permissions');
    expect(landingUrl({ ...ACCOUNT, permissions: [] })).toBe('/status');
  });

  it('only follows in-app return URLs', () => {
    expect(safeReturnUrl('/roles/new')).toBe('/roles/new');
    expect(safeReturnUrl('https://evil.test')).toBeNull();
    expect(safeReturnUrl('//evil.test')).toBeNull();
    expect(safeReturnUrl('/\\evil.test')).toBeNull();
    expect(safeReturnUrl('/login?returnUrl=%2Froles')).toBeNull();
    expect(safeReturnUrl(undefined)).toBeNull();
  });
});
