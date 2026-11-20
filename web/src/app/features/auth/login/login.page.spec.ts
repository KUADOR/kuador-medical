import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  ACCOUNT,
  BRANCH_SELECTION,
  CLINIC_SELECTION,
  FULL_ACCOUNT,
  LOGIN,
  PLATFORM_LOGIN,
  STAFF_ADMIN_ACCOUNT,
  SUPER_ADMIN,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  submit,
  type,
} from '../../../../testing/app-harness';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { expectDesignSystemScreen, primaryActions } from '../../../../testing/screen-rules';

describe('LoginPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(url = '/login') {
    await app.harness.navigateByUrl(url);
    app.harness.detectChanges();
  }

  async function login(account: object, response = LOGIN) {
    type(app.page(), '#login-email', 'owner@example.com');
    type(app.page(), '#login-password', 'a-long-example-passphrase');
    submit(app.page());
    app.backend.expectOne('/api/v1/auth/login').flush(response);
    await app.settle();
    app.backend.expectOne('/api/v1/me').flush(account);
    await app.settle();
  }

  it('has no accessibility violations, also with validation errors (axe)', async () => {
    await open();
    await expectNoAxeViolations(app.page());

    submit(app.page());
    await app.settle();
    await expectNoAxeViolations(app.page());
    app.backend.expectNone('/api/v1/auth/login');
    expectDesignSystemScreen(app.page());
  });

  it('is built with the design system and has one primary action: Ingresar', async () => {
    await open();
    expectDesignSystemScreen(app.page());
    expect(primaryActions(app.page()).map((b) => b.textContent?.trim())).toEqual(['Ingresar']);
    expect(app.page().querySelectorAll('kd-field')).toHaveLength(2);
  });

  it('keeps the flow in 3 actions (baseline F1): email, password and Ingresar', async () => {
    await open();
    // Every interactive step of the flow is counted; the audit baseline (F1) is 3.
    let actions = 0;
    type(app.page(), '#login-email', 'owner@example.com');
    actions++;
    type(app.page(), '#login-password', 'a-long-example-passphrase');
    actions++;
    submit(app.page());
    actions++;
    app.backend.expectOne('/api/v1/auth/login').flush(LOGIN);
    await app.settle();
    app.backend.expectOne('/api/v1/me').flush(STAFF_ADMIN_ACCOUNT);
    await app.settle();

    expect(actions).toBeLessThanOrEqual(3);
    // Staff who may read patients land on the search itself (baseline F4: 1 action, now 0).
    expect(app.router.url).toBe('/patients');
    app.backend.expectOne('/api/v1/patients?page=0&size=20').flush({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
    });
  });

  it('shows Spanish validation messages and sends nothing when the form is empty', async () => {
    await open();
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('Este campo es obligatorio.');
    expect(app.page().querySelector('#login-email')?.getAttribute('aria-invalid')).toBe('true');
    app.backend.expectNone('/api/v1/auth/login');
  });

  it('goes to the clinic roles after a tenant login', async () => {
    await open();
    await login(FULL_ACCOUNT);

    expect(app.router.url).toBe('/roles');
    app.backend.expectOne('/api/v1/roles').flush([]);
  });

  it('follows the return URL set by the guards', async () => {
    await open('/login?returnUrl=%2Froles%2Fnew');
    await login(FULL_ACCOUNT);

    expect(app.router.url).toBe('/roles/new');
    app.backend.expectOne('/api/v1/permissions').flush([]);
  });

  it('ignores a return URL to another site', async () => {
    await open('/login?returnUrl=https%3A%2F%2Fevil.test');
    await login({ ...ACCOUNT, permissions: [] });

    expect(app.router.url).toBe('/status');
    app.backend.expectOne('/api/v1/system/info').flush({});
  });

  it('forces the SUPER_ADMIN to change the initial password first', async () => {
    await open('/login?returnUrl=%2Fplatform%2Fpermissions');
    await login({ ...SUPER_ADMIN, mustChangePassword: true }, PLATFORM_LOGIN);

    expect(app.router.url).toBe('/change-password');
  });

  it('shows the translated error on wrong credentials and clears the password', async () => {
    await open();
    type(app.page(), '#login-email', 'owner@example.com');
    type(app.page(), '#login-password', 'wrong');
    submit(app.page());
    const { body, init } = problem(401, 'INVALID_CREDENTIALS');
    app.backend.expectOne('/api/v1/auth/login').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.INVALID_CREDENTIALS,
    );
    expect((app.page().querySelector('#login-password') as HTMLInputElement).value).toBe('');
    expect(app.router.url).toBe('/login');
  });

  it('continues on the clinic selection when the password matches several clinics', async () => {
    await open('/login?returnUrl=%2Froles%2Fnew');
    type(app.page(), '#login-email', 'staff@example.com');
    type(app.page(), '#login-password', 'a-long-example-passphrase');
    submit(app.page());
    app.backend.expectOne('/api/v1/auth/login').flush(CLINIC_SELECTION);
    await app.settle();

    expect(app.router.url).toBe('/login/clinic?returnUrl=%2Froles%2Fnew');
    expect(app.session.isAuthenticated()).toBe(false);
  });

  it('continues on the branch selection when the account has several branches', async () => {
    await open();
    type(app.page(), '#login-email', 'staff@example.com');
    type(app.page(), '#login-password', 'a-long-example-passphrase');
    submit(app.page());
    app.backend.expectOne('/api/v1/auth/login').flush(BRANCH_SELECTION);
    await app.settle();

    expect(app.router.url).toBe('/login/branch');
  });

  it('informs in Spanish when the access is temporarily blocked', async () => {
    await open();
    type(app.page(), '#login-email', 'owner@example.com');
    type(app.page(), '#login-password', 'wrong');
    submit(app.page());
    const { body, init } = problem(429, 'TOO_MANY_ATTEMPTS');
    app.backend.expectOne('/api/v1/auth/login').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.TOO_MANY_ATTEMPTS,
    );
  });

  it('links to the password recovery', async () => {
    await open();
    const link = app.page().querySelector('a[href="/forgot-password"]');
    expect(link?.textContent).toContain('¿Olvidaste tu contraseña?');
  });

  it('shows the notice left by the signup', async () => {
    TestBed.inject(NoticeService).show('Tu clínica quedó registrada.');
    await open();
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Tu clínica quedó registrada.');
  });
});
