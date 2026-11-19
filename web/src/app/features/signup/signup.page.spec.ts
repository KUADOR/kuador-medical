import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { FULL_ACCOUNT, LOGIN, problem } from '../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../testing/app-harness';

describe('SignupPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
    await app.harness.navigateByUrl('/signup');
    app.harness.detectChanges();
  });

  afterEach(() => app.backend.verify());

  function fill(confirm = 'a-long-example-passphrase') {
    type(app.page(), '#signup-clinic', 'Clínica Norte');
    type(app.page(), '#signup-name', 'Ana Pérez');
    type(app.page(), '#signup-email', 'ana@example.com');
    type(app.page(), '#signup-password', 'a-long-example-passphrase');
    type(app.page(), '#signup-confirm', confirm);
  }

  it('checks the password policy and the confirmation before calling the API', async () => {
    fill('different-passphrase');
    type(app.page(), '#signup-password', 'short');
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('Debe tener al menos 12 caracteres.');
    expect(app.page().textContent).toContain('Las contraseñas no coinciden.');
    app.backend.expectNone('/api/v1/tenants/signup');
  });

  it('registers the clinic, logs in and opens its roles as OWNER and ADMIN', async () => {
    fill();
    submit(app.page());

    const signup = app.backend.expectOne('/api/v1/tenants/signup');
    expect(signup.request.body).toEqual({
      clinicName: 'Clínica Norte',
      ownerDisplayName: 'Ana Pérez',
      email: 'ana@example.com',
      password: 'a-long-example-passphrase',
    });
    signup.flush(
      { tenantId: '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60' },
      { status: 201, statusText: 'Created' },
    );
    await app.settle();

    const login = app.backend.expectOne('/api/v1/auth/login');
    expect(login.request.body).toEqual({
      email: 'ana@example.com',
      password: 'a-long-example-passphrase',
    });
    login.flush(LOGIN);
    await app.settle();
    app.backend.expectOne('/api/v1/me').flush(FULL_ACCOUNT);
    await app.settle();

    expect(app.router.url).toBe('/roles');
    app.backend.expectOne('/api/v1/roles').flush([]);
  });

  it('marks the email when it is already registered', async () => {
    fill();
    submit(app.page());
    const { body, init } = problem(409, 'EMAIL_TAKEN');
    app.backend.expectOne('/api/v1/tenants/signup').flush(body, init);
    await app.settle();

    const email = app.page().querySelector('#signup-email');
    expect(email?.getAttribute('aria-invalid')).toBe('true');
    expect(app.page().querySelector('#signup-email-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.EMAIL_TAKEN,
    );
    expect(app.router.url).toBe('/signup');
  });

  it('sends to login with a notice when the clinic was created but the login failed', async () => {
    fill();
    submit(app.page());
    app.backend
      .expectOne('/api/v1/tenants/signup')
      .flush(
        { tenantId: '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60' },
        { status: 201, statusText: 'Created' },
      );
    await app.settle();
    const { body, init } = problem(500, 'INTERNAL_ERROR');
    app.backend.expectOne('/api/v1/auth/login').flush(body, init);
    await app.settle();

    expect(app.router.url).toBe('/login');
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Tu clínica quedó registrada');
  });

  it('is not shown to an active session', async () => {
    await app.harness.navigateByUrl('/status');
    app.backend.expectOne('/api/v1/system/info').flush({});
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/signup');
    expect(app.router.url).toBe('/roles');
    app.backend.expectOne('/api/v1/roles').flush([]);
  });
});
