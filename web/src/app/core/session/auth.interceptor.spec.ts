import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LOGIN, REFRESHED, problem } from '../../../testing/fixtures';
import { authInterceptor } from './auth.interceptor';
import { SessionStore } from './session.store';

/** Lets the interceptor resolve its token (a promise) and send the request. */
const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: SessionStore;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('adds the bearer token to API requests', async () => {
    session.start(LOGIN);
    http.get('/api/v1/roles').subscribe();
    await tick();

    const request = backend.expectOne('/api/v1/roles');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush([]);
  });

  it('never sends the token to other origins', () => {
    session.start(LOGIN);
    http.get('https://third-party.test/data').subscribe();

    const request = backend.expectOne('https://third-party.test/data');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('never sends the token to the public auth operations', () => {
    session.start(LOGIN);
    http.post('/api/v1/auth/login', {}).subscribe();
    http.post('/api/v1/public/clinics/demo/patient-registrations', {}).subscribe();

    for (const url of ['/api/v1/auth/login', '/api/v1/public/clinics/demo/patient-registrations']) {
      const request = backend.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({});
    }
  });

  it('sends no header without a session', async () => {
    http.get('/api/v1/system/info').subscribe();
    await tick();

    const request = backend.expectOne('/api/v1/system/info');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('refreshes an expired access token before sending the request', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    session.start(LOGIN);
    vi.setSystemTime(Date.now() + LOGIN.expiresIn * 1000);

    const response = firstValueFrom(http.get('/api/v1/roles'));
    await tick();
    const refresh = backend.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'test-refresh' });
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush(REFRESHED);
    await tick();

    const request = backend.expectOne('/api/v1/roles');
    expect(request.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    request.flush([]);
    await expect(response).resolves.toEqual([]);
    expect(session.refreshToken()).toBe('refreshed-refresh');
  });

  it('refreshes once on 401 and retries the request with the new token', async () => {
    session.start(LOGIN);
    const response = firstValueFrom(http.get('/api/v1/roles'));
    await tick();
    const { body, init } = problem(401, 'UNAUTHENTICATED');
    backend.expectOne('/api/v1/roles').flush(body, init);
    await tick();

    backend.expectOne('/api/v1/auth/refresh').flush(REFRESHED);
    await tick();

    const retried = backend.expectOne('/api/v1/roles');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    retried.flush([{ id: 'r1' }]);
    await expect(response).resolves.toEqual([{ id: 'r1' }]);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('shares one refresh between concurrent requests that get 401', async () => {
    session.start(LOGIN);
    const first = firstValueFrom(http.get('/api/v1/roles'));
    const second = firstValueFrom(http.get('/api/v1/permissions'));
    await tick();
    const { body, init } = problem(401, 'UNAUTHENTICATED');
    backend.expectOne('/api/v1/roles').flush(body, init);
    backend.expectOne('/api/v1/permissions').flush(body, init);
    await tick();

    backend.expectOne('/api/v1/auth/refresh').flush(REFRESHED);
    await tick();

    backend.expectOne('/api/v1/roles').flush([]);
    backend.expectOne('/api/v1/permissions').flush([]);
    await expect(first).resolves.toEqual([]);
    await expect(second).resolves.toEqual([]);
  });

  it('ends the session and goes to login when the refresh token is rejected', async () => {
    session.start(LOGIN);
    const response = firstValueFrom(http.get('/api/v1/roles'));
    await tick();
    const unauthenticated = problem(401, 'UNAUTHENTICATED');
    backend.expectOne('/api/v1/roles').flush(unauthenticated.body, unauthenticated.init);
    await tick();
    const rejected = problem(401, 'INVALID_SESSION_TOKEN');
    backend.expectOne('/api/v1/auth/refresh').flush(rejected.body, rejected.init);

    await expect(response).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('ends the session when the retried request is rejected again', async () => {
    session.start(LOGIN);
    const response = firstValueFrom(http.get('/api/v1/roles'));
    await tick();
    const { body, init } = problem(401, 'UNAUTHENTICATED');
    backend.expectOne('/api/v1/roles').flush(body, init);
    await tick();
    backend.expectOne('/api/v1/auth/refresh').flush(REFRESHED);
    await tick();
    backend.expectOne('/api/v1/roles').flush(body, init);

    await expect(response).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('does not redirect when the login itself answers 401', async () => {
    const response = firstValueFrom(http.post('/api/v1/auth/login', {}));
    const { body, init } = problem(401, 'INVALID_CREDENTIALS');
    backend.expectOne('/api/v1/auth/login').flush(body, init);

    await expect(response).rejects.toMatchObject({ status: 401 });
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('keeps the session when the current password is wrong on a password change', async () => {
    session.start(LOGIN);
    const response = firstValueFrom(http.post('/api/v1/auth/password', {}));
    await tick();
    const { body, init } = problem(401, 'INVALID_CREDENTIALS');
    backend.expectOne('/api/v1/auth/password').flush(body, init);

    await expect(response).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
    backend.expectNone('/api/v1/auth/refresh');
  });

  it('sends to the password change on 403 PASSWORD_CHANGE_REQUIRED', async () => {
    session.start(LOGIN);
    const response = firstValueFrom(http.get('/api/v1/roles'));
    await tick();
    const { body, init } = problem(403, 'PASSWORD_CHANGE_REQUIRED');
    backend.expectOne('/api/v1/roles').flush(body, init);

    await expect(response).rejects.toMatchObject({ status: 403 });
    expect(session.mustChangePassword()).toBe(true);
    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('leaves a plain 403 FORBIDDEN to the caller', async () => {
    session.start(LOGIN);
    const response = firstValueFrom(http.get('/api/v1/roles'));
    await tick();
    const { body, init } = problem(403, 'FORBIDDEN');
    backend.expectOne('/api/v1/roles').flush(body, init);

    await expect(response).rejects.toMatchObject({ status: 403 });
    expect(session.isAuthenticated()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
