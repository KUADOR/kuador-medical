import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ACCOUNT, LOGIN } from '../../../testing/fixtures';
import { SessionStore } from './session.store';

describe('SessionStore', () => {
  let store: SessionStore;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(SessionStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('starts without a session', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
  });

  it('keeps the token in sessionStorage, not localStorage', () => {
    store.start(LOGIN);
    expect(store.accessToken()).toBe('test-token');
    expect(sessionStorage.getItem('mk.session')).toContain('test-token');
    expect(localStorage.length).toBe(0);
  });

  it('stops handing out the access token shortly before it expires, but keeps the session', () => {
    vi.useFakeTimers();
    store.start({ ...LOGIN, expiresIn: 60 });
    vi.advanceTimersByTime(31_000);

    expect(store.accessToken()).toBeNull();
    expect(store.refreshToken()).toBe('test-refresh');
    expect(store.isAuthenticated()).toBe(true);
    expect(store.hasUsableSession()).toBe(true);
  });

  it('ends the session once the refresh token expires', () => {
    vi.useFakeTimers();
    store.start({ ...LOGIN, refreshExpiresIn: 120 });
    vi.advanceTimersByTime(121_000);

    expect(store.refreshToken()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('mk.session')).toBeNull();
  });

  it('replaces the tokens after a refresh without reloading GET /me', async () => {
    store.start(LOGIN);
    const loading = store.ensureAccount();
    http.expectOne('/api/v1/me').flush(ACCOUNT);
    await loading;

    store.replaceTokens({ ...LOGIN, accessToken: 'next-token', refreshToken: 'next-refresh' });

    expect(store.accessToken()).toBe('next-token');
    expect(store.refreshToken()).toBe('next-refresh');
    expect(store.account()).toEqual(ACCOUNT);
    http.expectNone('/api/v1/me');
  });

  it('exposes the plan modules of GET /me', async () => {
    store.start(LOGIN);
    const loading = store.ensureAccount();
    http.expectOne('/api/v1/me').flush({ ...ACCOUNT, enabledModules: ['patients'] });
    await loading;

    expect(store.hasModule('patients')).toBe(true);
    expect(store.hasModule('identity')).toBe(false);
  });

  it('ignores a stored session of the previous format (without refresh token)', () => {
    sessionStorage.setItem(
      'mk.session',
      JSON.stringify({
        accessToken: 'old',
        expiresAt: Date.now() + 60_000,
        mustChangePassword: false,
      }),
    );
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('mk.session')).toBeNull();
    http = TestBed.inject(HttpTestingController);
  });

  it('loads GET /me once and exposes its permissions', async () => {
    store.start(LOGIN);
    const first = store.ensureAccount();
    http.expectOne('/api/v1/me').flush(ACCOUNT);

    expect(await first).toEqual(ACCOUNT);
    expect(await store.ensureAccount()).toEqual(ACCOUNT);
    expect(store.hasPermission('roles.read')).toBe(true);
    expect(store.hasPermission('roles.delete')).toBe(false);
  });

  it('shares one GET /me between concurrent callers', async () => {
    store.start(LOGIN);
    const first = store.ensureAccount();
    const second = store.ensureAccount();
    http.expectOne('/api/v1/me').flush(ACCOUNT);

    expect(await first).toEqual(ACCOUNT);
    expect(await second).toEqual(ACCOUNT);
  });

  it('drops a GET /me that answers after the session ended', async () => {
    store.start(LOGIN);
    const loading = store.ensureAccount();
    store.clear();
    http.expectOne('/api/v1/me').flush(ACCOUNT);

    expect(await loading).toBeNull();
    expect(store.account()).toBeNull();
  });

  it('reports a pending password change from the login and from /me', async () => {
    store.start({ ...LOGIN, mustChangePassword: true });
    expect(store.mustChangePassword()).toBe(true);

    const loading = store.ensureAccount();
    http.expectOne('/api/v1/me').flush({ ...ACCOUNT, mustChangePassword: false });
    await loading;
    expect(store.mustChangePassword()).toBe(false);

    store.markPasswordChangeRequired();
    expect(store.mustChangePassword()).toBe(true);
  });

  it('returns null when /me fails', async () => {
    store.start(LOGIN);
    const loading = store.ensureAccount();
    http.expectOne('/api/v1/me').flush(null, { status: 500, statusText: 'Server Error' });
    expect(await loading).toBeNull();
  });

  it('clears everything', () => {
    store.start(LOGIN);
    store.clear();
    expect(store.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('mk.session')).toBeNull();
  });
});
