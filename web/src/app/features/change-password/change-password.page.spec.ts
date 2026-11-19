import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { PLATFORM_LOGIN, SUPER_ADMIN, problem } from '../../../testing/fixtures';
import {
  AppHarness,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../testing/app-harness';
import { CURRENT_PASSWORD_WRONG_MESSAGE } from './change-password.page';

const PENDING = { ...SUPER_ADMIN, mustChangePassword: true };

describe('ChangePasswordPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
    await signIn(app, PENDING, PLATFORM_LOGIN);
    await app.harness.navigateByUrl('/change-password');
    app.harness.detectChanges();
  });

  afterEach(() => app.backend.verify());

  function fill(current = 'initial-secret', next = 'a-brand-new-passphrase', confirm = next) {
    type(app.page(), '#current-password', current);
    type(app.page(), '#new-password', next);
    type(app.page(), '#confirm-password', confirm);
  }

  it('explains that the change is mandatory', () => {
    expect(byTestId(app.page(), 'required-notice')?.textContent).toContain(
      'debes reemplazar tu contraseña inicial',
    );
  });

  it('blocks every other page until the password changes', async () => {
    await app.harness.navigateByUrl('/platform/permissions');
    expect(app.router.url).toBe('/change-password');
  });

  it('validates the policy on the client', async () => {
    fill('same-passphrase-123', 'same-passphrase-123', 'other');
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('La contraseña nueva debe ser distinta de la actual.');
    expect(app.page().textContent).toContain('Las contraseñas no coinciden.');
    app.backend.expectNone('/api/v1/auth/password');
  });

  it('changes the password and opens the landing page of the account', async () => {
    fill();
    submit(app.page());
    const request = app.backend.expectOne('/api/v1/auth/password');
    expect(request.request.body).toEqual({
      currentPassword: 'initial-secret',
      newPassword: 'a-brand-new-passphrase',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await app.settle();
    app.backend.expectOne('/api/v1/me').flush(SUPER_ADMIN);
    await app.settle();

    expect(app.router.url).toBe('/platform/permissions');
    app.backend.expectOne('/api/v1/platform/permissions').flush([]);
  });

  it('keeps the session and marks the field when the current password is wrong', async () => {
    fill('wrong');
    submit(app.page());
    const { body, init } = problem(401, 'INVALID_CREDENTIALS');
    app.backend.expectOne('/api/v1/auth/password').flush(body, init);
    await app.settle();

    expect(app.session.isAuthenticated()).toBe(true);
    expect(app.router.url).toBe('/change-password');
    expect(app.page().querySelector('#current-password-error')?.textContent?.trim()).toBe(
      CURRENT_PASSWORD_WRONG_MESSAGE,
    );
  });

  it('shows the policy message on WEAK_PASSWORD', async () => {
    fill();
    submit(app.page());
    const { body, init } = problem(400, 'WEAK_PASSWORD');
    app.backend.expectOne('/api/v1/auth/password').flush(body, init);
    await app.settle();

    expect(app.page().querySelector('#new-password-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.WEAK_PASSWORD,
    );
  });

  it('lets the person leave without changing it', async () => {
    buttonByText(app.page(), 'Salir').click();
    await app.settle();

    expect(app.session.isAuthenticated()).toBe(false);
    expect(app.router.url).toBe('/login');
    app.backend
      .expectOne('/api/v1/auth/logout')
      .flush(null, { status: 204, statusText: 'No Content' });
  });
});
