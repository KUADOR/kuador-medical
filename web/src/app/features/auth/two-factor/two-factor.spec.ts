import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { FULL_ACCOUNT, LOGIN, problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../../testing/app-harness';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';
import {
  ENROLLMENT,
  OWNER_ACCOUNT,
  OWNER_PENDING_LOGIN,
  OWNER_VERIFIED_LOGIN,
  twoFactorRequired,
} from '../../../../testing/owner-fixtures';
import { qrPath } from './qr-code';
import { LOST_DEVICE_HELP } from './two-factor-code';
import { groupSecret } from './two-factor-enroll.page';

describe('Second factor of the OWNER (ADR-019)', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  /** Logs in with the form; GET /me answers that the second factor is still pending. */
  async function loginPendingTwoFactor(
    step: 'ENROLLMENT_REQUIRED' | 'VERIFICATION_REQUIRED',
    url = '/login',
  ) {
    await app.harness.navigateByUrl(url);
    type(app.page(), '#login-email', 'owner@example.test');
    type(app.page(), '#login-password', 'a-long-example-passphrase');
    submit(app.page());
    app.backend.expectOne('/api/v1/auth/login').flush(OWNER_PENDING_LOGIN);
    await app.settle();
    const { body, init } = twoFactorRequired(step);
    app.backend.expectOne('/api/v1/me').flush(body, init);
    await app.settle();
  }

  /** A session that the api already told to pass the second factor. */
  async function pendingSession(step: 'ENROLLMENT_REQUIRED' | 'VERIFICATION_REQUIRED') {
    app.session.start(OWNER_PENDING_LOGIN, step);
  }

  describe('login', () => {
    it('asks for the code after the password when GET /me requires the verification', async () => {
      await loginPendingTwoFactor('VERIFICATION_REQUIRED', '/login?returnUrl=%2Fdata-export');

      // A 401 TWO_FACTOR_REQUIRED keeps the session: no refresh, no logout.
      app.backend.expectNone('/api/v1/auth/refresh');
      expect(app.session.isAuthenticated()).toBe(true);
      expect(app.session.twoFactorStep()).toBe('VERIFICATION_REQUIRED');
      expect(app.router.url).toBe('/two-factor/verify?returnUrl=%2Fdata-export');
      expect(app.page().textContent).toContain('Verificación en dos pasos');
      expectMigratedScreen(app.page());
    });

    it('sends an OWNER without the second factor to the mandatory enrollment', async () => {
      await loginPendingTwoFactor('ENROLLMENT_REQUIRED');

      expect(app.router.url).toBe('/two-factor/enroll');
      app.backend.expectOne('/api/v1/auth/two-factor/enrollment').flush(ENROLLMENT);
    });

    it('does not change the login of accounts without a second factor', async () => {
      await app.harness.navigateByUrl('/login');
      type(app.page(), '#login-email', 'staff@example.test');
      type(app.page(), '#login-password', 'a-long-example-passphrase');
      submit(app.page());
      app.backend.expectOne('/api/v1/auth/login').flush(LOGIN);
      await app.settle();
      app.backend.expectOne('/api/v1/me').flush(FULL_ACCOUNT);
      await app.settle();

      expect(app.session.twoFactorStep()).toBeNull();
      expect(app.router.url).toBe('/roles');
      app.backend.match(() => true);
    });
  });

  describe('verification page', () => {
    beforeEach(async () => {
      await pendingSession('VERIFICATION_REQUIRED');
      await app.harness.navigateByUrl('/two-factor/verify?returnUrl=%2Fdata-export');
      app.harness.detectChanges();
    });

    it('opens the session with a valid code and goes back to the return URL', async () => {
      type(app.page(), '#two-factor-code', '123 456');
      submit(app.page());

      const verify = app.backend.expectOne('/api/v1/auth/two-factor/verify');
      expect(verify.request.body).toEqual({ code: '123456' });
      expect(verify.request.headers.get('Authorization')).toBe('Bearer owner-pending-token');
      verify.flush(OWNER_VERIFIED_LOGIN);
      await app.settle();
      app.backend.expectOne('/api/v1/me').flush(OWNER_ACCOUNT);
      await app.settle();

      expect(app.session.twoFactorStep()).toBeNull();
      expect(app.session.accessToken()).toBe('owner-verified-token');
      expect(app.router.url).toBe('/data-export');
      app.backend.match(() => true);
    });

    it('rejects a malformed code without calling the API', async () => {
      type(app.page(), '#two-factor-code', '12a');
      submit(app.page());
      await app.settle();

      app.backend.expectNone('/api/v1/auth/two-factor/verify');
      expect(app.page().textContent).toContain(
        'Escribe los 6 dígitos que muestra tu app de autenticación.',
      );
    });

    it('shows a wrong code on its field, in Spanish', async () => {
      type(app.page(), '#two-factor-code', '000000');
      submit(app.page());
      const { body, init } = problem(400, 'TWO_FACTOR_CODE_INVALID');
      app.backend.expectOne('/api/v1/auth/two-factor/verify').flush(body, init);
      await app.settle();

      const field = app.page().querySelector('#two-factor-code');
      expect(field?.getAttribute('aria-invalid')).toBe('true');
      expect(app.page().textContent).toContain(ERROR_MESSAGES.TWO_FACTOR_CODE_INVALID);
      expect(app.router.url).toContain('/two-factor/verify');
    });

    it('explains the temporary block after too many attempts', async () => {
      type(app.page(), '#two-factor-code', '000000');
      submit(app.page());
      const { body, init } = problem(429, 'TOO_MANY_ATTEMPTS');
      app.backend.expectOne('/api/v1/auth/two-factor/verify').flush(body, init);
      await app.settle();

      expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.TOO_MANY_ATTEMPTS,
      );
    });

    it('tells how to recover a lost device and lets the person leave', async () => {
      expect(app.page().textContent).toContain(LOST_DEVICE_HELP);
      const leave = Array.from(app.page().querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Salir'),
      );
      leave?.click();
      await app.settle();

      expect(app.session.isAuthenticated()).toBe(false);
      expect(app.router.url).toBe('/login');
      app.backend.match('/api/v1/auth/logout');
    });

    it('has no accessibility violations (axe)', async () => {
      await expectNoAxeViolations(app.page());
    });
  });

  describe('enrollment page', () => {
    beforeEach(async () => {
      await pendingSession('ENROLLMENT_REQUIRED');
      await app.harness.navigateByUrl('/two-factor/enroll');
      app.harness.detectChanges();
    });

    async function loaded() {
      const start = app.backend.expectOne('/api/v1/auth/two-factor/enrollment');
      expect(start.request.method).toBe('POST');
      start.flush(ENROLLMENT);
      await app.settle();
    }

    it('shows the QR code of the otpauth URI and the grouped secret', async () => {
      await loaded();

      const qr = byTestId(app.page(), 'two-factor-qr');
      expect(qr?.querySelector('svg')?.getAttribute('role')).toBe('img');
      expect(qr?.querySelector('svg')?.getAttribute('aria-label')).toContain('Código QR');
      expect(qr?.querySelector('path')?.getAttribute('d')).toBe(qrPath(ENROLLMENT.otpauthUri).path);
      expect(byTestId(app.page(), 'two-factor-secret')?.textContent).toBe(
        'JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP',
      );
      expectMigratedScreen(app.page());
      await expectNoAxeViolations(app.page());
    });

    it('enables the second factor with the first code and opens the session', async () => {
      await loaded();
      type(app.page(), '#two-factor-code', '654321');
      submit(app.page());

      const confirm = app.backend.expectOne('/api/v1/auth/two-factor/enrollment/confirm');
      expect(confirm.request.body).toEqual({ code: '654321' });
      confirm.flush(OWNER_VERIFIED_LOGIN);
      await app.settle();
      app.backend.expectOne('/api/v1/me').flush(OWNER_ACCOUNT);
      await app.settle();

      expect(app.session.twoFactorStep()).toBeNull();
      expect(app.router.url).toBe('/roles');
      app.backend.match(() => true);
    });

    it('shows a wrong first code on its field', async () => {
      await loaded();
      type(app.page(), '#two-factor-code', '000000');
      submit(app.page());
      const { body, init } = problem(400, 'TWO_FACTOR_CODE_INVALID');
      app.backend.expectOne('/api/v1/auth/two-factor/enrollment/confirm').flush(body, init);
      await app.settle();

      expect(app.page().querySelector('#two-factor-code')?.getAttribute('aria-invalid')).toBe(
        'true',
      );
      expect(app.session.twoFactorStep()).toBe('ENROLLMENT_REQUIRED');
    });

    it('goes to the verification when the account already has the second factor', async () => {
      const { body, init } = problem(409, 'TWO_FACTOR_ALREADY_ENABLED');
      app.backend.expectOne('/api/v1/auth/two-factor/enrollment').flush(body, init);
      await app.settle();

      expect(app.session.twoFactorStep()).toBe('VERIFICATION_REQUIRED');
      expect(app.router.url).toBe('/two-factor/verify');
    });
  });

  describe('guards', () => {
    it('sends every protected page to the pending step, keeping the return URL', async () => {
      await pendingSession('ENROLLMENT_REQUIRED');
      await app.harness.navigateByUrl('/roles');
      await app.settle();

      // No GET /me while the second factor is pending (it would answer 401).
      app.backend.expectNone('/api/v1/me');
      expect(app.router.url).toBe('/two-factor/enroll?returnUrl=%2Froles');
      app.backend.match('/api/v1/auth/two-factor/enrollment');
    });

    it('sends the login page to the pending step instead of a second login', async () => {
      await pendingSession('VERIFICATION_REQUIRED');
      await app.harness.navigateByUrl('/login');
      await app.settle();

      expect(app.router.url).toBe('/two-factor/verify');
    });

    it('keeps the step pages closed to other steps and to visitors', async () => {
      await app.harness.navigateByUrl('/two-factor/verify');
      expect(app.router.url).toBe('/login');

      await pendingSession('VERIFICATION_REQUIRED');
      await app.harness.navigateByUrl('/two-factor/enroll');
      expect(app.router.url).toBe('/two-factor/verify');
    });

    it('sends a session that already passed the second factor home', async () => {
      await signIn(app, OWNER_ACCOUNT, OWNER_VERIFIED_LOGIN);
      await app.harness.navigateByUrl('/two-factor/verify');

      expect(app.router.url).toBe('/roles');
      app.backend.match(() => true);
    });

    it('remembers the pending step after a reload (sessionStorage)', async () => {
      await pendingSession('VERIFICATION_REQUIRED');
      const stored = JSON.parse(sessionStorage.getItem('mk.session') ?? '{}') as {
        twoFactor?: string;
      };
      expect(stored.twoFactor).toBe('VERIFICATION_REQUIRED');
    });
  });
});

describe('qrPath', () => {
  it('draws the finder pattern inside a 4-module quiet zone', () => {
    const { path, size } = qrPath('otpauth://totp/Example?secret=JBSWY3DPEHPK3PXP');
    // Version 3 or 4 (29 or 33 modules) for this length, plus the quiet zone on both sides.
    expect(size - 8).toBeGreaterThanOrEqual(21);
    expect((size - 8 - 17) % 4).toBe(0);
    // The top-left module of the finder pattern is always dark; the quiet zone never is.
    expect(path.startsWith('M4 4h1v1h-1z')).toBe(true);
    expect(path).not.toContain('M0 0');
  });

  it('groups the secret in blocks of 4 without padding', () => {
    expect(groupSecret('ABCDEFGHIJ==')).toBe('ABCD EFGH IJ');
  });
});
