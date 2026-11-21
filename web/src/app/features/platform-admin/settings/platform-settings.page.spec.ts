import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import {
  DEFAULT_SETTINGS,
  FULL_ACCOUNT,
  PLATFORM_ADMIN,
  SUPER_ADMIN,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
  signIn,
} from '../../../../testing/app-harness';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';

describe('PlatformSettingsPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open() {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/settings');
    await app.settle();
    app.backend.expectOne('/api/v1/platform/settings').flush(DEFAULT_SETTINGS);
    await app.settle();
  }

  function setValue(key: string, value: string): void {
    const element = app.page().querySelector<HTMLInputElement>(`#setting-${key}`);
    if (!element) {
      throw new Error(`No setting ${key}`);
    }
    element.value = value;
    element.dispatchEvent(new Event('input'));
    element.dispatchEvent(new Event('blur'));
  }

  function submitForm(): void {
    app.page().querySelector('form')?.dispatchEvent(new Event('submit'));
  }

  it('shows the six configurable periods with their current values', async () => {
    await open();
    const inputs = allByTestId(app.page(), 'setting') as HTMLInputElement[];
    expect(inputs.map((element) => element.value)).toEqual(['14', '7', '90', '90', '72', '30']);
    const labels = Array.from(app.page().querySelectorAll('.kd-field__label')).map((label) =>
      label.textContent?.trim(),
    );
    expect(labels).toContain('Días de gracia tras el vencimiento (días)');
    expect(labels).toContain('Vigencia de la invitación al OWNER (horas)');
  });

  it('saves the settings with the version read', async () => {
    await open();
    setValue('graceDays', '5');
    submitForm();
    const request = app.backend.expectOne({ method: 'PUT', url: '/api/v1/platform/settings' });
    expect(request.request.body).toEqual({ ...DEFAULT_SETTINGS, graceDays: 5 });
    request.flush({ ...DEFAULT_SETTINGS, graceDays: 5, version: 2 });
    await app.settle();
    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe(
      'Se guardaron los ajustes. Aplican a los plazos que se calculen desde ahora.',
    );
  });

  it('validates the contract bounds before calling the API', async () => {
    await open();
    setValue('trialDays', '0');
    setValue('graceDays', '1.5');
    setValue('retentionDays', '');
    setValue('ownerInvitationTtlHours', '721');
    submitForm();
    await app.settle();
    const errors = Array.from(app.page().querySelectorAll('.kd-field__error')).map((e) =>
      e.textContent?.trim(),
    );
    expect(errors).toEqual([
      'Debe ser como mínimo 1.',
      'Escribe un número entero, sin decimales.',
      'Este campo es obligatorio.',
      'Debe ser como máximo 720.',
    ]);
  });

  it('offers to reload when the settings changed meanwhile', async () => {
    await open();
    submitForm();
    const { body, init } = problem(409, 'VERSION_CONFLICT');
    app.backend.expectOne('/api/v1/platform/settings').flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'form-error')?.textContent).toContain(
      ERROR_MESSAGES.VERSION_CONFLICT,
    );
    expect(app.page().textContent).toContain('Recargar los ajustes');
  });

  it('puts server validation errors on their field', async () => {
    await open();
    submitForm();
    const { body, init } = problem(400, 'VALIDATION', [
      { field: 'retentionDays', code: 'INVALID_FORMAT' },
    ]);
    app.backend.expectOne('/api/v1/platform/settings').flush(body, init);
    await app.settle();
    const field = app.page().querySelector('#setting-retentionDays');
    expect(field?.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows a retry when the settings cannot be loaded', async () => {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/settings');
    await app.settle();
    const { body, init } = problem(500, 'INTERNAL_ERROR');
    app.backend.expectOne('/api/v1/platform/settings').flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.INTERNAL_ERROR,
    );
    expectMigratedScreen(app.page());
  });

  it('uses only the design system', async () => {
    await open();
    expectMigratedScreen(app.page());
  });

  it('is closed to clinic accounts and to platform accounts without the permission', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/platform/settings');
    expect(app.router.url).toBe('/forbidden');

    app.session.clear();
    await signIn(app, SUPER_ADMIN);
    await app.harness.navigateByUrl('/platform/settings');
    expect(app.router.url).toBe('/forbidden');
  });
});
