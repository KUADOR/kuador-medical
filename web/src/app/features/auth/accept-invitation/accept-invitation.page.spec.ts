import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { BRANCH_SELECTION, FULL_ACCOUNT, LOGIN, problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  submit,
  type,
} from '../../../../testing/app-harness';
import { INVITATION_INVALID_MESSAGE } from './accept-invitation.page';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';

const TOKEN = 'invitation-token-0123456789';
const PREVIEW = {
  clinicName: 'Test Clinic',
  maskedEmail: 'j***@example.com',
  kind: 'STAFF',
  expiresAt: '2030-01-04T15:00:00Z',
};

describe('AcceptInvitationPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(url = `/invitation#t=${TOKEN}`) {
    await app.harness.navigateByUrl(url);
    app.harness.detectChanges();
  }

  async function openValid() {
    await open();
    const preview = app.backend.expectOne('/api/v1/auth/invitations/preview');
    expect(preview.request.body).toEqual({ token: TOKEN });
    preview.flush(PREVIEW);
    await app.settle();
  }

  function fill(password = 'a-long-example-passphrase') {
    type(app.page(), '#invitation-password', password);
    type(app.page(), '#invitation-confirm', password);
  }

  it('reads the token from the fragment, hides it and shows the clinic and masked email', async () => {
    await openValid();

    expect(TestBed.inject(Location).path(true)).toBe('/invitation');
    const summary = byTestId(app.page(), 'invitation-summary')?.textContent ?? '';
    expect(summary).toContain('Test Clinic');
    expect(summary).toContain('j***@example.com');
  });

  it('rejects a link without token without calling the API', async () => {
    await open('/invitation');
    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      INVITATION_INVALID_MESSAGE,
    );
  });

  it('rejects an expired or used invitation in Spanish', async () => {
    await open();
    const { body, init } = problem(400, 'ONE_TIME_TOKEN_INVALID');
    app.backend.expectOne('/api/v1/auth/invitations/preview').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      INVITATION_INVALID_MESSAGE,
    );
    expect(app.page().querySelector('form')).toBeNull();
  });

  it('activates the account and logs in', async () => {
    await openValid();
    fill();
    submit(app.page());
    const accept = app.backend.expectOne('/api/v1/auth/invitations/accept');
    expect(accept.request.body).toEqual({ token: TOKEN, password: 'a-long-example-passphrase' });
    accept.flush(LOGIN);
    await app.settle();
    app.backend.expectOne('/api/v1/me').flush(FULL_ACCOUNT);
    await app.settle();

    expect(app.router.url).toBe('/roles');
    expect(app.session.isAuthenticated()).toBe(true);
    app.backend.expectOne('/api/v1/roles').flush([]);
  });

  it('continues on the branch selection when the account has several branches', async () => {
    await openValid();
    fill();
    submit(app.page());
    app.backend.expectOne('/api/v1/auth/invitations/accept').flush(BRANCH_SELECTION);
    await app.settle();

    expect(app.router.url).toBe('/login/branch');
  });

  it('keeps the form on a weak password (the token is not consumed)', async () => {
    await openValid();
    fill();
    submit(app.page());
    const { body, init } = problem(400, 'WEAK_PASSWORD');
    app.backend.expectOne('/api/v1/auth/invitations/accept').flush(body, init);
    await app.settle();

    expect(app.page().querySelector('form')).not.toBeNull();
    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.WEAK_PASSWORD,
    );
  });

  it('offers a retry when the preview fails for network reasons', async () => {
    await open();
    app.backend.expectOne('/api/v1/auth/invitations/preview').error(new ProgressEvent('error'), {
      status: 0,
    });
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent).toContain('No pudimos conectar');
  });

  it('uses only the design system (MEDK-44)', async () => {
    await open();
    expectMigratedScreen(app.page());
    const preview = app.backend.expectOne('/api/v1/auth/invitations/preview');
    preview.flush(PREVIEW);
    await app.settle();
    expectMigratedScreen(app.page());

    fill('short');
    submit(app.page());
    await app.settle();
    expect(app.page().querySelector('#invitation-password')?.getAttribute('aria-describedby')).toBe(
      'invitation-password-hint invitation-password-error',
    );
    expectMigratedScreen(app.page());
  });
});
