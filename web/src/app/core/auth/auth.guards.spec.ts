import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { ACCOUNT, LOGIN, SUPER_ADMIN } from '../../../testing/fixtures';
import type { CurrentAccount } from '../api/api-types';
import {
  guestGuard,
  homeGuard,
  pendingPasswordChangeGuard,
  requirePermissions,
} from './auth.guards';
import { SessionStore } from './session.store';

describe('auth guards', () => {
  let backend: HttpTestingController;
  let session: SessionStore;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
  });

  afterEach(() => backend.verify());

  function run(guard: CanActivateFn, url = '/roles') {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as Promise<boolean | UrlTree>;
  }

  async function runWithAccount(guard: CanActivateFn, account: CurrentAccount) {
    session.start({ ...LOGIN, mustChangePassword: account.mustChangePassword });
    const result = run(guard);
    await Promise.resolve();
    backend.expectOne('/api/v1/me').flush(account);
    return result;
  }

  function serialize(result: boolean | UrlTree): string | boolean {
    return typeof result === 'boolean' ? result : router.serializeUrl(result);
  }

  it('sends to login with the return URL when there is no session', async () => {
    expect(serialize(await run(requirePermissions('roles.read')))).toBe(
      '/login?returnUrl=%2Froles',
    );
  });

  it('sends to login when /me rejects the token', async () => {
    session.start(LOGIN);
    const result = run(requirePermissions('roles.read'));
    await Promise.resolve();
    backend.expectOne('/api/v1/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(serialize(await result)).toBe('/login?returnUrl=%2Froles');
    expect(session.isAuthenticated()).toBe(false);
  });

  it('blocks everything while the password change is pending', async () => {
    const result = await runWithAccount(requirePermissions('roles.read'), {
      ...ACCOUNT,
      mustChangePassword: true,
    });
    expect(serialize(result)).toBe('/change-password');
  });

  it('sends to forbidden when a permission is missing', async () => {
    const result = await runWithAccount(requirePermissions('roles.read', 'roles.delete'), ACCOUNT);
    expect(serialize(result)).toBe('/forbidden');
  });

  it('lets in when every permission is present', async () => {
    const result = await runWithAccount(requirePermissions('roles.read', 'roles.create'), ACCOUNT);
    expect(result).toBe(true);
  });

  it('does not grant clinic permissions to a platform account', async () => {
    const result = await runWithAccount(requirePermissions('roles.read'), {
      ...ACCOUNT,
      scope: 'PLATFORM',
      tenant: undefined,
      roles: ['SUPER_ADMIN'],
      permissions: ['platform.permissions.read'],
    });
    expect(serialize(result)).toBe('/forbidden');
  });

  it('opens the password change page only while it is pending', async () => {
    expect(
      await runWithAccount(pendingPasswordChangeGuard, { ...ACCOUNT, mustChangePassword: true }),
    ).toBe(true);

    session.clear();
    expect(serialize(await runWithAccount(pendingPasswordChangeGuard, ACCOUNT))).toBe('/');
  });

  it('sends visitors of the root to the system status and accounts to their landing page', async () => {
    expect(serialize(await run(homeGuard, '/'))).toBe('/status');
    expect(serialize(await runWithAccount(homeGuard, ACCOUNT))).toBe('/roles');

    session.clear();
    expect(serialize(await runWithAccount(homeGuard, SUPER_ADMIN))).toBe('/platform/permissions');

    session.clear();
    expect(
      serialize(await runWithAccount(homeGuard, { ...SUPER_ADMIN, mustChangePassword: true })),
    ).toBe('/change-password');
  });

  it('opens login and signup only without a session', async () => {
    expect(await run(guestGuard, '/login')).toBe(true);
    expect(serialize(await runWithAccount(guestGuard, ACCOUNT))).toBe('/roles');
  });
});
