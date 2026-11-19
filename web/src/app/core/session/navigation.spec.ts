import {
  ACCOUNT,
  FULL_ACCOUNT,
  PATIENT_ACCOUNT,
  RECEPTION_ACCOUNT,
  SUPER_ADMIN,
} from '../../../testing/fixtures';
import { canAccess, landingUrl, menuFor, safeReturnUrl } from './navigation';

describe('navigation', () => {
  it('builds the menu from the permissions of GET /me', () => {
    expect(menuFor(FULL_ACCOUNT).map((item) => item.label)).toEqual(['Roles']);
    expect(menuFor(SUPER_ADMIN).map((item) => item.label)).toEqual(['Catálogo de permisos']);
    expect(menuFor({ ...ACCOUNT, permissions: [] })).toEqual([]);
    expect(menuFor(null)).toEqual([]);
  });

  it('hides entries whose module the clinic plan does not enable', () => {
    expect(menuFor({ ...FULL_ACCOUNT, enabledModules: ['patients'] })).toEqual([]);
    expect(landingUrl({ ...FULL_ACCOUNT, enabledModules: [] })).toBe('/status');
  });

  it('requires every permission and the module of a requirement', () => {
    expect(
      canAccess(ACCOUNT, { permissions: ['roles.read', 'roles.create'], module: 'identity' }),
    ).toBe(true);
    expect(
      canAccess({ ...ACCOUNT, permissions: ['roles.read'] }, { permissions: ['roles.create'] }),
    ).toBe(false);
    expect(canAccess({ ...ACCOUNT, enabledModules: [] }, { module: 'identity' })).toBe(false);
    expect(canAccess(SUPER_ADMIN, { permissions: ['platform.permissions.read'] })).toBe(true);
    expect(canAccess(null, {})).toBe(false);
  });

  it('shows the patients entry with patients.read and the patients module', () => {
    expect(menuFor(RECEPTION_ACCOUNT).map((item) => item.label)).toEqual(['Pacientes']);
    expect(landingUrl(RECEPTION_ACCOUNT)).toBe('/patients');
    expect(menuFor({ ...RECEPTION_ACCOUNT, enabledModules: ['identity'] })).toEqual([]);
  });

  it('gives patient accounts only their own pages', () => {
    expect(menuFor(PATIENT_ACCOUNT).map((item) => item.label)).toEqual([
      'Mis contactos autorizados',
    ]);
    expect(landingUrl(PATIENT_ACCOUNT)).toBe('/my-contacts');
    expect(canAccess(PATIENT_ACCOUNT, { kinds: ['PATIENT'] })).toBe(true);
    expect(canAccess(RECEPTION_ACCOUNT, { kinds: ['PATIENT'] })).toBe(false);
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
    expect(safeReturnUrl('/invitation')).toBeNull();
    expect(safeReturnUrl('/reset-password')).toBeNull();
    expect(safeReturnUrl(undefined)).toBeNull();
  });
});
