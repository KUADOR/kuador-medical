import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ACCOUNT, LOGIN, PLATFORM_LOGIN, SUPER_ADMIN } from '../../../testing/fixtures';
import { AuthService } from './auth.service';
import { SessionStore } from './session.store';

describe('AuthService', () => {
  let auth: AuthService;
  let session: SessionStore;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    session = TestBed.inject(SessionStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('logs in, keeps the token and loads GET /me', async () => {
    const result = auth.login({ email: ' owner@example.com ', password: 'secret' });

    const request = backend.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'owner@example.com', password: 'secret' });
    request.flush(LOGIN);
    await Promise.resolve();
    backend.expectOne('/api/v1/me').flush(ACCOUNT);

    expect(await result).toEqual(ACCOUNT);
    expect(session.accessToken()).toBe('test-token');
  });

  it('does not start a session when the login fails', async () => {
    const result = auth.login({ email: 'owner@example.com', password: 'wrong' });
    backend
      .expectOne('/api/v1/auth/login')
      .flush(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'INVALID_CREDENTIALS' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(false);
  });

  it('signs up a clinic with trimmed data and never sends a tenant id', async () => {
    const result = auth.signUp({
      clinicName: ' Clínica Norte ',
      ownerDisplayName: ' Ana ',
      email: ' ana@example.com ',
      password: ' a long passphrase ',
    });

    const request = backend.expectOne('/api/v1/tenants/signup');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      clinicName: 'Clínica Norte',
      ownerDisplayName: 'Ana',
      email: 'ana@example.com',
      password: ' a long passphrase ',
    });
    request.flush(
      { tenantId: '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60' },
      { status: 201, statusText: 'Created' },
    );

    expect(await result).toEqual({ tenantId: '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60' });
    expect(session.isAuthenticated()).toBe(false);
  });

  it('changes the password, clears the pending flag and reloads GET /me', async () => {
    session.start(PLATFORM_LOGIN);
    expect(session.mustChangePassword()).toBe(true);

    const result = auth.changePassword({
      currentPassword: 'initial',
      newPassword: 'a-new-passphrase',
    });
    const request = backend.expectOne('/api/v1/auth/password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      currentPassword: 'initial',
      newPassword: 'a-new-passphrase',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    backend.expectOne('/api/v1/me').flush(SUPER_ADMIN);

    expect(await result).toEqual(SUPER_ADMIN);
    expect(session.mustChangePassword()).toBe(false);
    expect(sessionStorage.getItem('mk.session')).toContain('"mustChangePassword":false');
  });

  it('logs out by dropping the token', () => {
    session.start(LOGIN);
    auth.logout();
    expect(session.isAuthenticated()).toBe(false);
  });
});
