import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  submit,
  type,
} from '../../../../testing/app-harness';
import { PASSWORD_RESET_DONE_MESSAGE } from './reset-password.page';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';

const TOKEN = 'reset-token-0123456789abcdef';

describe('ResetPasswordPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(url = `/password-reset#t=${TOKEN}`) {
    await app.harness.navigateByUrl(url);
    app.harness.detectChanges();
  }

  function fill(password = 'a-brand-new-passphrase', confirm = password) {
    type(app.page(), '#new-password', password);
    type(app.page(), '#confirm-password', confirm);
  }

  it('takes the token from the fragment and removes it from the address bar', async () => {
    await open();
    expect(TestBed.inject(Location).path(true)).toBe('/password-reset');
    expect(app.page().querySelector('form')).not.toBeNull();
  });

  it('rejects a link without token with a Spanish message', async () => {
    await open('/password-reset');
    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.ONE_TIME_TOKEN_INVALID,
    );
    expect(app.page().querySelector('a[href="/forgot-password"]')).not.toBeNull();
  });

  it('checks length and confirmation before sending', async () => {
    await open();
    fill('short', 'different');
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('Debe tener al menos 12 caracteres.');
    expect(app.page().textContent).toContain('Las contraseñas no coinciden.');
    app.backend.expectNone('/api/v1/auth/password-reset/confirm');
  });

  it('sets the new password and goes to login with a notice', async () => {
    await open();
    fill();
    submit(app.page());
    const request = app.backend.expectOne('/api/v1/auth/password-reset/confirm');
    expect(request.request.body).toEqual({ token: TOKEN, newPassword: 'a-brand-new-passphrase' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await app.settle();

    expect(app.router.url).toBe('/login');
    expect(byTestId(app.page(), 'notice')?.textContent).toContain(PASSWORD_RESET_DONE_MESSAGE);
  });

  it('rejects an expired or used link with a Spanish message', async () => {
    await open();
    fill();
    submit(app.page());
    const { body, init } = problem(400, 'ONE_TIME_TOKEN_INVALID');
    app.backend.expectOne('/api/v1/auth/password-reset/confirm').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.ONE_TIME_TOKEN_INVALID,
    );
    expect(app.page().querySelector('form')).toBeNull();
  });

  it('marks a weak password on its field', async () => {
    await open();
    fill();
    submit(app.page());
    const { body, init } = problem(400, 'WEAK_PASSWORD');
    app.backend.expectOne('/api/v1/auth/password-reset/confirm').flush(body, init);
    await app.settle();

    expect(app.page().querySelector('#new-password')?.getAttribute('aria-invalid')).toBe('true');
    expect(app.page().textContent).toContain(ERROR_MESSAGES.WEAK_PASSWORD);
  });

  it('uses only the design system (MEDK-44)', async () => {
    await open();
    expect(app.page().querySelector('#new-password')?.getAttribute('aria-describedby')).toBe(
      'new-password-hint',
    );
    expectMigratedScreen(app.page());

    fill('short', 'different');
    submit(app.page());
    await app.settle();
    expect(app.page().querySelector('#new-password')?.getAttribute('aria-describedby')).toBe(
      'new-password-hint new-password-error',
    );
    expect(app.page().querySelector('#confirm-password-error')?.textContent).toContain(
      'Las contraseñas no coinciden.',
    );
    expectMigratedScreen(app.page());
  });

  it('uses only the design system on an invalid link (MEDK-44)', async () => {
    await open('/password-reset');
    expectMigratedScreen(app.page());
  });
});
