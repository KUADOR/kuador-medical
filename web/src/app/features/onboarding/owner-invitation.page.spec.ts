import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { problem } from '../../../testing/fixtures';
import { AppHarness, byTestId, createAppHarness, submit, type } from '../../../testing/app-harness';
import { expectNoAxeViolations } from '../../../testing/axe';
import { expectMigratedScreen } from '../../../testing/migrated-screen';
import { OWNER_PENDING_LOGIN, OWNER_PREVIEW, OWNER_TOKEN } from '../../../testing/owner-fixtures';
import { OUTDATED_TERMS_MESSAGE, OWNER_INVITATION_INVALID_MESSAGE } from './owner-invitation.page';

describe('OwnerInvitationPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(url = `/owner-invitation#t=${OWNER_TOKEN}`) {
    await app.harness.navigateByUrl(url);
    app.harness.detectChanges();
  }

  async function openValid() {
    await open();
    const preview = app.backend.expectOne('/api/v1/public/owner-invitations/preview');
    expect(preview.request.body).toEqual({ token: OWNER_TOKEN });
    expect(preview.request.headers.has('Authorization')).toBe(false);
    preview.flush(OWNER_PREVIEW);
    await app.settle();
  }

  function check(selector: string) {
    const box = app.page().querySelector<HTMLInputElement>(selector);
    if (!box) {
      throw new Error(`No checkbox ${selector}`);
    }
    box.click();
  }

  function fill(password = 'a-long-example-passphrase') {
    type(app.page(), '#owner-password', password);
    type(app.page(), '#owner-confirm', password);
    check('#owner-terms');
    check('#owner-data-policy');
  }

  it('reads the token from the fragment, hides it and shows the clinic and the versions', async () => {
    await openValid();

    expect(TestBed.inject(Location).path(true)).toBe('/owner-invitation');
    const summary = byTestId(app.page(), 'invitation-summary')?.textContent ?? '';
    expect(summary).toContain('Example Clinic');
    expect(summary).toContain('Jane Doe');
    expect(summary).toContain('j***@example.com');
    expect(summary).toContain('4 de enero de 2030');
    expect(app.page().textContent).toContain('términos del servicio (versión 2030-01)');
    expect(app.page().textContent).toContain('política de tratamiento de datos (versión 2030-01)');
    expectMigratedScreen(app.page());
  });

  it('rejects a link without token without calling the API', async () => {
    await open('/owner-invitation');
    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      OWNER_INVITATION_INVALID_MESSAGE,
    );
  });

  it('rejects an expired, used or replaced invitation in Spanish (410)', async () => {
    await open();
    const { body, init } = problem(410, 'INVITATION_INVALID_OR_EXPIRED');
    app.backend.expectOne('/api/v1/public/owner-invitations/preview').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'link-error')?.textContent?.trim()).toBe(
      OWNER_INVITATION_INVALID_MESSAGE,
    );
    expect(app.page().querySelector('form')).toBeNull();
  });

  it('requires the password, its confirmation and both acceptances before calling the API', async () => {
    await openValid();
    type(app.page(), '#owner-password', 'a-long-example-passphrase');
    type(app.page(), '#owner-confirm', 'a-long-example-passphrase');
    submit(app.page());
    await app.settle();

    app.backend.expectNone('/api/v1/public/owner-invitations/accept');
    expect(app.page().querySelector('#owner-terms')?.getAttribute('aria-invalid')).toBe('true');
    expect(app.page().querySelector('#owner-data-policy')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
    expect(app.page().textContent).toContain('Debes aceptarlo para continuar.');
  });

  it('activates the account with the accepted versions and continues to the 2FA enrollment', async () => {
    await openValid();
    fill();
    submit(app.page());

    const accept = app.backend.expectOne('/api/v1/public/owner-invitations/accept');
    expect(accept.request.body).toEqual({
      token: OWNER_TOKEN,
      password: 'a-long-example-passphrase',
      acceptTerms: true,
      termsVersion: '2030-01',
      acceptDataPolicy: true,
      dataPolicyVersion: '2030-01',
    });
    accept.flush(OWNER_PENDING_LOGIN);
    await app.settle();

    // The session cannot read GET /me until the second factor: no call is made.
    app.backend.expectNone('/api/v1/me');
    expect(app.session.twoFactorStep()).toBe('ENROLLMENT_REQUIRED');
    expect(app.router.url).toBe('/two-factor/enroll');
    app.backend.expectOne('/api/v1/auth/two-factor/enrollment');
  });

  it('shows a weak password on its field without losing the invitation', async () => {
    await openValid();
    fill('short-but-12');
    submit(app.page());
    const { body, init } = problem(400, 'WEAK_PASSWORD');
    app.backend.expectOne('/api/v1/public/owner-invitations/accept').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.WEAK_PASSWORD,
    );
    expect(app.page().querySelector('#owner-password')?.getAttribute('aria-invalid')).toBe('true');
    expect(app.session.isAuthenticated()).toBe(false);
  });

  it('reloads the versions and asks again when the terms changed meanwhile', async () => {
    await openValid();
    fill();
    submit(app.page());
    const { body, init } = problem(400, 'VALIDATION', [
      { field: 'termsVersion', code: 'OUTDATED_VERSION' },
    ]);
    app.backend.expectOne('/api/v1/public/owner-invitations/accept').flush(body, init);
    await app.settle();

    app.backend
      .expectOne('/api/v1/public/owner-invitations/preview')
      .flush({ ...OWNER_PREVIEW, termsVersion: '2030-02' });
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(OUTDATED_TERMS_MESSAGE);
    expect(app.page().textContent).toContain('términos del servicio (versión 2030-02)');
    expect(app.page().querySelector<HTMLInputElement>('#owner-terms')?.checked).toBe(false);
  });

  it('goes to the invalid state when the invitation was used meanwhile', async () => {
    await openValid();
    fill();
    submit(app.page());
    const { body, init } = problem(410, 'INVITATION_INVALID_OR_EXPIRED');
    app.backend.expectOne('/api/v1/public/owner-invitations/accept').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'link-error')).not.toBeNull();
  });

  it('has no accessibility violations (axe)', async () => {
    await openValid();
    await expectNoAxeViolations(app.page());
  });
});
