// MEDK-30: the web against the local api, mocked with the examples of
// contracts/local-integration.openapi.yaml (POST /api/v1/auth/login, GET /api/v1/me).
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import type { AuthenticatedResult, CurrentSession } from './core/api/api-types';
import { App } from './app';
import { routes } from './app.routes';
import { ERROR_MESSAGES, NETWORK_ERROR_MESSAGE } from './core/errors/error-messages';
import { authInterceptor } from './core/session/auth.interceptor';
import { environment } from '../environments/environment';

/** `authenticated` example of POST /api/v1/auth/login. */
const CONTRACT_LOGIN: AuthenticatedResult = {
  status: 'AUTHENTICATED',
  accessToken: 'eyJhbGciOiJIUzI1NiJ9.example-payload.example-signature',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'refresh-example-token',
  refreshExpiresIn: 604800,
  scope: 'TENANT',
  clinic: { id: '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60', name: 'Example Clinic A' },
  branch: { id: '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e', name: 'Main branch' },
  mustChangePassword: false,
};

/** `staff` example of GET /api/v1/me. */
const CONTRACT_ME: CurrentSession = {
  accountId: '0f1e2d3c-4b5a-4968-8776-655443322110',
  kind: 'STAFF',
  scope: 'TENANT',
  displayName: 'Jane Doe',
  clinic: { id: '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60', name: 'Example Clinic A' },
  activeBranch: { id: '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e', name: 'Main branch' },
  availableBranches: [{ id: '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e', name: 'Main branch' }],
  roles: ['OWNER'],
  permissions: ['roles.read', 'identity.users.read', 'patients.read'],
  enabledModules: ['identity', 'patients'],
  mustChangePassword: false,
};

const LOGIN_URL = '/api/v1/auth/login';
const ME_URL = '/api/v1/me';

describe('Web against the local api (contract mock)', () => {
  let backend: HttpTestingController;
  let root: HTMLElement;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    root = fixture.nativeElement as HTMLElement;
    await router.navigateByUrl('/login');
    await settle();
  });

  afterEach(() => {
    // The landing page after login loads its own data; only the contract calls matter here.
    backend.match((req) => req.url !== LOGIN_URL && req.url !== ME_URL);
    backend.verify();
  });

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await TestBed.inject(Router).navigated;
    TestBed.tick();
  }

  function fill(selector: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(selector);
    if (!input) {
      throw new Error(`No field ${selector}`);
    }
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  async function submitLogin(): Promise<void> {
    fill('#login-email', 'jane.doe@example.com');
    fill('#login-password', 'a-long-example-passphrase');
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    await settle();
  }

  function formError(): string | undefined {
    return root.querySelector('[data-testid="form-error"]')?.textContent?.trim();
  }

  it('uses a relative API base in development so the dev server proxy reaches the api', () => {
    expect(environment.apiBaseUrl).toBe('/api/v1');
  });

  it('logs in and shows the clinic name served by GET /me', async () => {
    await submitLogin();
    const login = backend.expectOne(LOGIN_URL);
    expect(login.request.method).toBe('POST');
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush(CONTRACT_LOGIN);
    await settle();
    backend.expectOne(ME_URL).flush(CONTRACT_ME);
    await settle();

    expect(router.url).not.toBe('/login');
    expect(root.querySelector('[data-testid="account-name"]')?.textContent).toContain(
      'Example Clinic A',
    );
  });

  it('sends the access token on GET /me and no tenant identifier', async () => {
    await submitLogin();
    backend.expectOne(LOGIN_URL).flush(CONTRACT_LOGIN);
    await settle();
    const me = backend.expectOne(ME_URL);

    expect(me.request.headers.get('Authorization')).toBe(`Bearer ${CONTRACT_LOGIN.accessToken}`);
    expect(me.request.params.keys()).toEqual([]);
    expect(me.request.headers.keys().filter((name) => /tenant|clinic/i.test(name))).toEqual([]);
    me.flush(CONTRACT_ME);
    await settle();
  });

  it('shows the Spanish credentials message on 401 and stays on the login', async () => {
    await submitLogin();
    backend
      .expectOne(LOGIN_URL)
      .flush(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'INVALID_CREDENTIALS' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await settle();

    expect(formError()).toBe(ERROR_MESSAGES.INVALID_CREDENTIALS);
    expect(router.url).toBe('/login');
    backend.expectNone(ME_URL);
  });

  it('says in Spanish that the server could not be reached when the network fails', async () => {
    await submitLogin();
    backend.expectOne(LOGIN_URL).error(new ProgressEvent('error'), { status: 0 });
    await settle();

    expect(formError()).toBe(NETWORK_ERROR_MESSAGE);
    expect(router.url).toBe('/login');
  });

  it('says the same when the dev server proxy cannot reach the api (empty 502)', async () => {
    await submitLogin();
    backend.expectOne(LOGIN_URL).flush('', { status: 502, statusText: 'Bad Gateway' });
    await settle();

    expect(formError()).toBe(NETWORK_ERROR_MESSAGE);
  });

  it('says the same when a gateway times out waiting for the api (504)', async () => {
    await submitLogin();
    backend.expectOne(LOGIN_URL).flush('<html>Gateway Timeout</html>', {
      status: 504,
      statusText: 'Gateway Timeout',
    });
    await settle();

    expect(formError()).toBe(NETWORK_ERROR_MESSAGE);
  });
});
