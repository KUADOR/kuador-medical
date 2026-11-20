import {
  ACCOUNT,
  FULL_ACCOUNT,
  PATIENT_ACCOUNT,
  RECEPTION_ACCOUNT,
  STAFF_ADMIN_ACCOUNT,
  STAFF_OWNER_ACCOUNT,
  SUPER_ADMIN,
} from '../../../testing/fixtures';
import { canAccess, isOwner, landingUrl, menuFor, safeReturnUrl } from './navigation';

describe('navigation', () => {
  it('builds the menu from the permissions of GET /me', () => {
    expect(menuFor(FULL_ACCOUNT).map((item) => item.label)).toEqual(['Roles']);
    expect(menuFor(SUPER_ADMIN).map((item) => item.label)).toEqual(['Catálogo de permisos']);
    expect(menuFor({ ...ACCOUNT, permissions: [] })).toEqual([]);
    expect(menuFor(null)).toEqual([]);
  });

  it('puts the patient search first for quick access and lands there (MEDK-42)', () => {
    expect(menuFor(STAFF_ADMIN_ACCOUNT).map((item) => item.label)).toEqual([
      'Pacientes',
      'Personal',
      'Roles',
    ]);
    expect(landingUrl(STAFF_ADMIN_ACCOUNT)).toBe('/patients');
    expect(
      menuFor({ ...STAFF_ADMIN_ACCOUNT, enabledModules: ['identity'] }).map((item) => item.label),
    ).toEqual(['Personal', 'Roles']);
    expect(landingUrl({ ...STAFF_ADMIN_ACCOUNT, enabledModules: ['identity'] })).toBe('/staff');
    expect(
      menuFor({ ...STAFF_ADMIN_ACCOUNT, enabledModules: ['patients'] }).map((item) => item.label),
    ).toEqual(['Pacientes']);
  });

  it('hides the patient search without patients.read even with the module (R9)', () => {
    const withoutPermission = {
      ...STAFF_ADMIN_ACCOUNT,
      permissions: STAFF_ADMIN_ACCOUNT.permissions.filter((code) => code !== 'patients.read'),
    };
    expect(menuFor(withoutPermission).map((item) => item.link)).not.toContain('/patients');
    expect(landingUrl(withoutPermission)).toBe('/staff');
  });

  it('gives every menu entry an icon of the design system', () => {
    for (const account of [STAFF_ADMIN_ACCOUNT, SUPER_ADMIN, PATIENT_ACCOUNT]) {
      for (const item of menuFor(account)) {
        expect(item.icon, item.label).toBeTruthy();
      }
    }
  });

  it('knows when the active assignment is OWNER', () => {
    expect(isOwner(STAFF_OWNER_ACCOUNT)).toBe(true);
    expect(isOwner(STAFF_ADMIN_ACCOUNT)).toBe(false);
    expect(isOwner(null)).toBe(false);
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
    expect(safeReturnUrl('/password-reset')).toBeNull();
    expect(safeReturnUrl(undefined)).toBeNull();
  });
});
