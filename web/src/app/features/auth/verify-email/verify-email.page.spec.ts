import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { problem } from '../../../../testing/fixtures';
import { AppHarness, byTestId, createAppHarness } from '../../../../testing/app-harness';
import { EMAIL_VERIFIED_MESSAGE } from './verify-email.page';

const TOKEN = 'verification-token-0123456789';

describe('VerifyEmailPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(url = `/verify-email#t=${TOKEN}`) {
    await app.harness.navigateByUrl(url);
    app.harness.detectChanges();
  }

  it('confirms the email with the token of the fragment and hides it', async () => {
    await open();
    expect(TestBed.inject(Location).path(true)).toBe('/verify-email');
    const request = app.backend.expectOne('/api/v1/auth/email-verification/confirm');
    expect(request.request.body).toEqual({ token: TOKEN });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe(EMAIL_VERIFIED_MESSAGE);
  });

  it('rejects an expired or used link in Spanish', async () => {
    await open();
    const { body, init } = problem(400, 'ONE_TIME_TOKEN_INVALID');
    app.backend.expectOne('/api/v1/auth/email-verification/confirm').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.ONE_TIME_TOKEN_INVALID,
    );
  });

  it('rejects a link without token without calling the API', async () => {
    await open('/verify-email');
    expect(byTestId(app.page(), 'link-error')).not.toBeNull();
  });
});
