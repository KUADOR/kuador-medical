import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  submit,
  type,
} from '../../../../testing/app-harness';
import { RESET_REQUESTED_MESSAGE } from './forgot-password.page';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';

describe('ForgotPasswordPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
    await app.harness.navigateByUrl('/forgot-password');
    app.harness.detectChanges();
  });

  afterEach(() => app.backend.verify());

  it('validates the email in Spanish before sending anything', async () => {
    type(app.page(), '#forgot-email', 'not-an-email');
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('Escribe un correo válido');
    app.backend.expectNone('/api/v1/auth/password-reset/request');
  });

  it('shows the same neutral confirmation whatever the email', async () => {
    type(app.page(), '#forgot-email', 'unknown@example.com');
    submit(app.page());
    const request = app.backend.expectOne('/api/v1/auth/password-reset/request');
    expect(request.request.body).toEqual({ email: 'unknown@example.com' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe(RESET_REQUESTED_MESSAGE);
    expect(app.page().querySelector('form')).toBeNull();
  });

  it('explains a temporary block in Spanish', async () => {
    type(app.page(), '#forgot-email', 'person@example.com');
    submit(app.page());
    const { body, init } = problem(429, 'TOO_MANY_ATTEMPTS');
    app.backend.expectOne('/api/v1/auth/password-reset/request').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.TOO_MANY_ATTEMPTS,
    );
  });

  it('uses only the design system (MEDK-44)', async () => {
    expectMigratedScreen(app.page());

    submit(app.page());
    await app.settle();
    expect(app.page().querySelector('#forgot-email')?.getAttribute('aria-describedby')).toBe(
      'forgot-email-error',
    );
    expectMigratedScreen(app.page());

    type(app.page(), '#forgot-email', 'person@example.com');
    submit(app.page());
    app.backend
      .expectOne('/api/v1/auth/password-reset/request')
      .flush(null, { status: 202, statusText: 'Accepted' });
    await app.settle();
    expectMigratedScreen(app.page());
  });
});
