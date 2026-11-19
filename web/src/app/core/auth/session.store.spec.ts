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

  it('drops the token once it expires', () => {
    vi.useFakeTimers();
    store.start({ ...LOGIN, expiresIn: 60 });
    vi.advanceTimersByTime(61_000);

    expect(store.accessToken()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('mk.session')).toBeNull();
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
