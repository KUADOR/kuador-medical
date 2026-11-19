import {
  PATIENTS_ADMIN_ACCOUNT,
  PATIENTS_READER_ACCOUNT,
  REGISTRATION_SETTINGS,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
} from '../../../../testing/app-harness';

describe('SelfRegistrationSettingsPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(account = PATIENTS_ADMIN_ACCOUNT, settings = REGISTRATION_SETTINGS) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/patients/self-registration');
    app.backend.expectOne('/api/v1/patient-registration-settings').flush(settings);
    await app.settle();
  }

  function toggle(): HTMLButtonElement {
    return byTestId(app.page(), 'self-registration-switch') as HTMLButtonElement;
  }

  it('is disabled by default and has no public link', async () => {
    await open();
    expect(toggle().getAttribute('role')).toBe('switch');
    expect(toggle().getAttribute('aria-checked')).toBe('false');
    expect(byTestId(app.page(), 'status')?.textContent).toContain('Deshabilitado');
    expect(byTestId(app.page(), 'public-link')).toBeNull();
    expect(byTestId(app.page(), 'no-link')).not.toBeNull();
  });

  it('enables self-registration with the version read and shows the public link', async () => {
    await open();
    toggle().click();
    const request = app.backend.expectOne({
      method: 'PUT',
      url: '/api/v1/patient-registration-settings',
    });
    expect(request.request.body).toEqual({ selfRegistrationEnabled: true, version: 4 });
    request.flush({ ...REGISTRATION_SETTINGS, selfRegistrationEnabled: true, version: 5 });
    await app.settle();

    expect(toggle().getAttribute('aria-checked')).toBe('true');
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se habilitó el autorregistro');
    const link = app.page().querySelector<HTMLInputElement>('#public-link');
    expect(link?.value).toBe(`${location.origin}/clinics/test-clinic/register`);
  });

  it('disables it again', async () => {
    await open(PATIENTS_ADMIN_ACCOUNT, { ...REGISTRATION_SETTINGS, selfRegistrationEnabled: true });
    expect(byTestId(app.page(), 'public-link')).not.toBeNull();
    toggle().click();
    const request = app.backend.expectOne('/api/v1/patient-registration-settings');
    expect(request.request.body).toEqual({ selfRegistrationEnabled: false, version: 4 });
    request.flush({ ...REGISTRATION_SETTINGS, version: 5 });
    await app.settle();
    expect(byTestId(app.page(), 'public-link')).toBeNull();
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se deshabilitó');
  });

  it('offers to reload after a version conflict', async () => {
    await open();
    toggle().click();
    const { body, init } = problem(409, 'VERSION_CONFLICT');
    app.backend.expectOne('/api/v1/patient-registration-settings').flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'form-error')?.textContent).toContain('Otra persona cambió');

    buttonByText(app.page(), 'Recargar').click();
    app.backend
      .expectOne('/api/v1/patient-registration-settings')
      .flush({ ...REGISTRATION_SETTINGS, selfRegistrationEnabled: true, version: 5 });
    await app.settle();
    expect(toggle().getAttribute('aria-checked')).toBe('true');
  });

  it('is read-only without patients.selfRegistration.configure', async () => {
    await open(PATIENTS_READER_ACCOUNT, {
      ...REGISTRATION_SETTINGS,
      selfRegistrationEnabled: true,
    });
    expect(byTestId(app.page(), 'self-registration-switch')).toBeNull();
    expect(byTestId(app.page(), 'read-only')).not.toBeNull();
    expect(byTestId(app.page(), 'public-link')).not.toBeNull();
  });
});
