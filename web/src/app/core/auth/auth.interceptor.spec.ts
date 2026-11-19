import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LOGIN } from '../../../testing/fixtures';
import { authInterceptor } from './auth.interceptor';
import { SessionStore } from './session.store';

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

  afterEach(() => backend.verify());

  it('adds the bearer token to API requests', () => {
    session.start(LOGIN);
    http.get('/api/v1/roles').subscribe();

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

  it('sends no header without a session', () => {
    http.get('/api/v1/system/info').subscribe();

    const request = backend.expectOne('/api/v1/system/info');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('ends the session and goes to login on 401', async () => {
    session.start(LOGIN);
    const call = firstValueFrom(http.get('/api/v1/me'));
    backend
      .expectOne('/api/v1/me')
      .flush(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHENTICATED' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('does not redirect when the login itself answers 401', async () => {
    const call = firstValueFrom(http.post('/api/v1/auth/login', {}));
    backend
      .expectOne('/api/v1/auth/login')
      .flush(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'INVALID_CREDENTIALS' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('keeps the session when the current password is wrong on a password change', async () => {
    session.start(LOGIN);
    const call = firstValueFrom(http.post('/api/v1/auth/password', {}));
    backend
      .expectOne('/api/v1/auth/password')
      .flush(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'INVALID_CREDENTIALS' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('sends to the password change on 403 PASSWORD_CHANGE_REQUIRED', async () => {
    session.start(LOGIN);
    const call = firstValueFrom(http.get('/api/v1/roles'));
    backend
      .expectOne('/api/v1/roles')
      .flush(
        { type: 'about:blank', title: 'Forbidden', status: 403, code: 'PASSWORD_CHANGE_REQUIRED' },
        { status: 403, statusText: 'Forbidden' },
      );

    await expect(call).rejects.toMatchObject({ status: 403 });
    expect(session.mustChangePassword()).toBe(true);
    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('leaves a plain 403 FORBIDDEN to the caller', async () => {
    session.start(LOGIN);
    const call = firstValueFrom(http.get('/api/v1/roles'));
    backend
      .expectOne('/api/v1/roles')
      .flush(
        { type: 'about:blank', title: 'Forbidden', status: 403, code: 'FORBIDDEN' },
        { status: 403, statusText: 'Forbidden' },
      );

    await expect(call).rejects.toMatchObject({ status: 403 });
    expect(session.isAuthenticated()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
