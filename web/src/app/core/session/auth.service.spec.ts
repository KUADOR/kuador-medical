import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ACCOUNT,
  BRANCH_SELECTION,
  CLINIC_SELECTION,
  LOGIN,
  MULTI_BRANCH_ACCOUNT,
  NORTH_BRANCH,
  OTHER_CLINIC,
  problem,
} from '../../../testing/fixtures';
import { AuthService, LoginStepExpiredError } from './auth.service';
import { SessionStore } from './session.store';

const tick = () => new Promise((resolve) => setTimeout(resolve));

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

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('logs in straight away when the password matches one clinic with one branch', async () => {
    const result = auth.login({ email: ' owner@example.com ', password: 'secret' });

    const request = backend.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'owner@example.com', password: 'secret' });
    request.flush(LOGIN);
    await tick();
    backend.expectOne('/api/v1/me').flush(ACCOUNT);

    expect(await result).toEqual({ status: 'AUTHENTICATED', account: ACCOUNT });
    expect(session.accessToken()).toBe('test-token');
    expect(session.refreshToken()).toBe('test-refresh');
  });

  it('does not start a session when the login fails', async () => {
    const result = auth.login({ email: 'owner@example.com', password: 'wrong' });
    const { body, init } = problem(401, 'INVALID_CREDENTIALS');
    backend.expectOne('/api/v1/auth/login').flush(body, init);

    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated()).toBe(false);
  });

  it('asks for the clinic, then the branch, then starts the session', async () => {
    const login = auth.login({ email: 'staff@example.com', password: 'secret' });
    backend.expectOne('/api/v1/auth/login').flush(CLINIC_SELECTION);
    expect(await login).toEqual({ status: 'CLINIC_SELECTION_REQUIRED' });
    expect(auth.pendingStep()).toEqual(CLINIC_SELECTION);
    expect(session.isAuthenticated()).toBe(false);
    // The pre-auth token is never stored in the browser.
    expect(sessionStorage.length).toBe(0);

    const clinic = auth.selectClinic(OTHER_CLINIC.id);
    const clinicRequest = backend.expectOne('/api/v1/auth/login/clinic');
    expect(clinicRequest.request.body).toEqual({
      preAuthToken: 'pre-auth-token',
      clinicId: OTHER_CLINIC.id,
    });
    clinicRequest.flush(BRANCH_SELECTION);
    expect(await clinic).toEqual({ status: 'BRANCH_SELECTION_REQUIRED' });

    const branch = auth.selectBranch(NORTH_BRANCH.id);
    const branchRequest = backend.expectOne('/api/v1/auth/login/branch');
    expect(branchRequest.request.body).toEqual({
      preAuthToken: 'pre-auth-token-2',
      branchId: NORTH_BRANCH.id,
    });
    branchRequest.flush(LOGIN);
    await tick();
    backend.expectOne('/api/v1/me').flush(MULTI_BRANCH_ACCOUNT);

    expect(await branch).toEqual({ status: 'AUTHENTICATED', account: MULTI_BRANCH_ACCOUNT });
    expect(auth.pendingStep()).toBeNull();
  });

  it('forgets the pending step once its pre-auth token expires', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    const login = auth.login({ email: 'staff@example.com', password: 'secret' });
    backend.expectOne('/api/v1/auth/login').flush(CLINIC_SELECTION);
    await login;

    vi.setSystemTime(Date.now() + CLINIC_SELECTION.preAuthExpiresIn * 1000);

    expect(auth.pendingStep()).toBeNull();
    await expect(auth.selectClinic(OTHER_CLINIC.id)).rejects.toBeInstanceOf(LoginStepExpiredError);
  });

  it('switches the branch with the current refresh token and reloads GET /me', async () => {
    session.start(LOGIN);
    const switched = auth.switchBranch(NORTH_BRANCH.id);
    const request = backend.expectOne('/api/v1/auth/switch-branch');
    expect(request.request.body).toEqual({
      branchId: NORTH_BRANCH.id,
      refreshToken: 'test-refresh',
    });
    request.flush({ ...LOGIN, accessToken: 'north-token', branch: NORTH_BRANCH });
    await tick();
    const north = { ...MULTI_BRANCH_ACCOUNT, activeBranch: NORTH_BRANCH };
    backend.expectOne('/api/v1/me').flush(north);

    expect(await switched).toEqual(north);
    expect(session.accessToken()).toBe('north-token');
  });

  it('logs out by revoking the refresh token and clearing the browser session', () => {
    session.start(LOGIN);
    auth.logout();

    expect(session.isAuthenticated()).toBe(false);
    const request = backend.expectOne('/api/v1/auth/logout');
    expect(request.request.body).toEqual({ refreshToken: 'test-refresh' });
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('ends the session even if the logout request fails', () => {
    session.start(LOGIN);
    auth.logout();
    backend.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));

    expect(session.isAuthenticated()).toBe(false);
  });

  it('requests a password reset with the trimmed email', async () => {
    const done = auth.requestPasswordReset(' person@example.com ');
    const request = backend.expectOne('/api/v1/auth/password-reset/request');
    expect(request.request.body).toEqual({ email: 'person@example.com' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await done;
  });

  it('sends one-time tokens only in the body, never in the URL', async () => {
    const token = 'one-time-token-0123456789';
    const preview = auth.previewInvitation(token);
    const reset = auth.confirmPasswordReset(token, 'a-new-long-passphrase');
    const verify = auth.confirmEmail(token);

    const requests = [
      backend.expectOne('/api/v1/auth/invitations/preview'),
      backend.expectOne('/api/v1/auth/password-reset/confirm'),
      backend.expectOne('/api/v1/auth/email-verification/confirm'),
    ];
    for (const request of requests) {
      expect(request.request.urlWithParams).not.toContain(token);
      expect(request.request.body.token).toBe(token);
    }
    expect(requests[1].request.body.newPassword).toBe('a-new-long-passphrase');
    requests[0].flush({
      clinicName: 'Test Clinic',
      maskedEmail: 'p***@example.com',
      kind: 'STAFF',
      expiresAt: '2030-01-04T15:00:00Z',
    });
    requests[1].flush(null, { status: 204, statusText: 'No Content' });
    requests[2].flush(null, { status: 204, statusText: 'No Content' });
    await Promise.all([preview, reset, verify]);
  });

  it('accepts an invitation and continues like a login', async () => {
    const accepted = auth.acceptInvitation('one-time-token-0123456789', 'a-long-passphrase');
    const request = backend.expectOne('/api/v1/auth/invitations/accept');
    expect(request.request.body).toEqual({
      token: 'one-time-token-0123456789',
      password: 'a-long-passphrase',
    });
    request.flush(BRANCH_SELECTION);

    expect(await accepted).toEqual({ status: 'BRANCH_SELECTION_REQUIRED' });
    expect(auth.pendingStep()).toEqual(BRANCH_SELECTION);
  });

  it('self-registers with the Idempotency-Key header on the public clinic path', async () => {
    const done = auth.selfRegister(
      'demo-clinic',
      {
        documentType: 'NATIONAL_ID',
        documentNumber: '2222222222',
        firstNames: 'Alex',
        lastNames: 'Poe',
        birthDate: '1990-03-15',
        sex: 'FEMALE',
        email: 'alex@example.com',
        password: 'a-long-passphrase',
        consent: { textVersion: '2026-01' },
      },
      'key-12345678',
    );
    const request = backend.expectOne('/api/v1/public/clinics/demo-clinic/patient-registrations');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-12345678');
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await done;
  });

  it('signs up a clinic with trimmed data and never sends a tenant id', async () => {
    const result = auth.signUp({
      clinicName: '  Clínica Demo ',
      ownerDisplayName: ' Ana ',
      email: ' ana@example.com ',
      password: 'a-long-example-passphrase',
    });
    const request = backend.expectOne('/api/v1/tenants/signup');
    expect(request.request.body).toEqual({
      clinicName: 'Clínica Demo',
      ownerDisplayName: 'Ana',
      email: 'ana@example.com',
      password: 'a-long-example-passphrase',
    });
    request.flush(
      { tenantId: 't1', tenantName: 'Clínica Demo', accountId: 'a1' },
      { status: 201, statusText: 'Created' },
    );
    await result;
  });

  it('changes the password, clears the pending flag and reloads GET /me', async () => {
    session.start({ ...LOGIN, mustChangePassword: true });
    const result = auth.changePassword({ currentPassword: 'old', newPassword: 'a-new-passphrase' });
    backend
      .expectOne('/api/v1/auth/password')
      .flush(null, { status: 204, statusText: 'No Content' });
    await tick();
    backend.expectOne('/api/v1/me').flush(ACCOUNT);

    expect(await result).toEqual(ACCOUNT);
    expect(session.mustChangePassword()).toBe(false);
  });
});
