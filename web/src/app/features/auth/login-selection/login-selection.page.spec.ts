import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { AuthService } from '../../../core/session/auth.service';
import {
  BRANCH_SELECTION,
  CLINIC_SELECTION,
  FULL_ACCOUNT,
  LOGIN,
  NORTH_BRANCH,
  OTHER_CLINIC,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
} from '../../../../testing/app-harness';
import { LOGIN_STEP_EXPIRED_MESSAGE } from './login-selection.page';

describe('LoginSelectionPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => {
    app.backend.verify();
    vi.useRealTimers();
  });

  async function pending(step: typeof CLINIC_SELECTION | typeof BRANCH_SELECTION) {
    const login = TestBed.inject(AuthService).login({ email: 'a@example.com', password: 'x' });
    app.backend.expectOne('/api/v1/auth/login').flush(step);
    await login;
  }

  function choices(): string[] {
    return allByTestId(app.page(), 'choice').map((button) => button.textContent?.trim() ?? '');
  }

  it('sends back to the login form when no selection is pending (e.g. after a reload)', async () => {
    await app.harness.navigateByUrl('/login/clinic');
    expect(app.router.url).toBe('/login');
  });

  it('lists only the clinics where the password matched and goes on to the branch', async () => {
    await pending(CLINIC_SELECTION);
    await app.harness.navigateByUrl('/login/clinic?returnUrl=%2Froles%2Fnew');
    app.harness.detectChanges();

    expect(app.page().querySelector('h1')?.textContent).toContain('Elige la clínica');
    expect(choices()).toEqual(['Test Clinic', 'Other Dental']);

    allByTestId(app.page(), 'choice')[1].click();
    const request = app.backend.expectOne('/api/v1/auth/login/clinic');
    expect(request.request.body).toEqual({
      preAuthToken: 'pre-auth-token',
      clinicId: OTHER_CLINIC.id,
    });
    request.flush(BRANCH_SELECTION);
    await app.settle();

    expect(app.router.url).toBe('/login/branch?returnUrl=%2Froles%2Fnew');
    expect(app.page().querySelector('h1')?.textContent).toContain('Elige la sucursal');
    expect(app.page().textContent).toContain('Test Clinic');
    expect(choices()).toEqual(['Main branch', 'North branch']);
  });

  it('starts the session in the chosen branch and follows the return URL', async () => {
    await pending(BRANCH_SELECTION);
    await app.harness.navigateByUrl('/login/branch?returnUrl=%2Froles%2Fnew');
    app.harness.detectChanges();

    allByTestId(app.page(), 'choice')[1].click();
    const request = app.backend.expectOne('/api/v1/auth/login/branch');
    expect(request.request.body).toEqual({
      preAuthToken: 'pre-auth-token-2',
      branchId: NORTH_BRANCH.id,
    });
    request.flush(LOGIN);
    await app.settle();
    app.backend.expectOne('/api/v1/me').flush({ ...FULL_ACCOUNT, activeBranch: NORTH_BRANCH });
    await app.settle();

    expect(app.router.url).toBe('/roles/new');
    expect(app.session.isAuthenticated()).toBe(true);
    app.backend.expectOne('/api/v1/permissions').flush([]);
  });

  it('goes back to the login with a Spanish notice when the pre-auth token expired', async () => {
    await pending(CLINIC_SELECTION);
    await app.harness.navigateByUrl('/login/clinic');
    app.harness.detectChanges();

    allByTestId(app.page(), 'choice')[0].click();
    const { body, init } = problem(401, 'INVALID_SESSION_TOKEN');
    app.backend.expectOne('/api/v1/auth/login/clinic').flush(body, init);
    await app.settle();

    expect(app.router.url).toBe('/login');
    expect(byTestId(app.page(), 'notice')?.textContent).toContain(LOGIN_STEP_EXPIRED_MESSAGE);
  });

  it('shows BRANCH_NOT_ASSIGNED in Spanish and stays on the page', async () => {
    await pending(BRANCH_SELECTION);
    await app.harness.navigateByUrl('/login/branch');
    app.harness.detectChanges();

    allByTestId(app.page(), 'choice')[0].click();
    const { body, init } = problem(403, 'BRANCH_NOT_ASSIGNED');
    app.backend.expectOne('/api/v1/auth/login/branch').flush(body, init);
    await app.settle();

    expect(app.router.url).toBe('/login/branch');
    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.BRANCH_NOT_ASSIGNED,
    );
  });
});
