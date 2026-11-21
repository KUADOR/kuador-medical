import { FULL_ACCOUNT, problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../../testing/app-harness';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';
import {
  PLATFORM_ADMIN,
  TENANTS_ONLY_ADMIN,
  TENANT_PAGE,
} from '../../../../testing/platform-tenant-fixtures';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { menuFor } from '../../../core/session/navigation';

describe('TenantsListPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(account = PLATFORM_ADMIN) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/platform/tenants');
    await app.settle();
    app.backend.expectOne('/api/v1/platform/tenants?page=0&size=20').flush(TENANT_PAGE);
    await app.settle();
  }

  it('lists each clinic with its plan, subscription status and onboarding date', async () => {
    await open();

    const rows = allByTestId(app.page(), 'tenant-row').map((row) =>
      Array.from(row.querySelectorAll('th, td')).map((cell) => cell.textContent?.trim()),
    );
    expect(rows).toEqual([
      [
        'Example Clinic S.A.Example Clinic',
        '0000000000001',
        'Médica',
        'Standard',
        'Activa',
        '1 de enero de 2030',
      ],
      [
        'Sample Dental Cía. Ltda.',
        '0000000000002',
        'Dental',
        'Prueba',
        'Prueba',
        '5 de enero de 2030',
      ],
    ]);
    const link = app.page().querySelector('[data-testid="tenant-row"] a');
    expect(link?.getAttribute('href')).toBe(
      '/platform/tenants/5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60',
    );
    expect(byTestId(app.page(), 'range')?.textContent?.trim()).toBe('Mostrando 1–2 de 2');
  });

  it('searches by name or RUC and filters by subscription status', async () => {
    await open();

    type(app.page(), '#tenants-q', '  0000000000001 ');
    const status = app.page().querySelector('#tenants-status') as HTMLSelectElement;
    status.value = 'SUSPENDED';
    status.dispatchEvent(new Event('change'));
    submit(app.page());
    await app.settle();

    const request = app.backend.expectOne(
      (req) => req.url === '/api/v1/platform/tenants' && req.params.get('status') === 'SUSPENDED',
    );
    expect(request.request.params.get('q')).toBe('0000000000001');
    expect(request.request.params.get('page')).toBe('0');
    request.flush({ ...TENANT_PAGE, items: [], totalItems: 0 });
    await app.settle();

    expect(byTestId(app.page(), 'empty')).not.toBeNull();
  });

  it('offers every subscription status in Spanish as a filter', async () => {
    await open();
    const labels = Array.from(
      (app.page().querySelector('#tenants-status') as HTMLSelectElement).options,
      (option) => option.textContent?.trim(),
    );
    expect(labels).toEqual(['Todos', 'Prueba', 'Activa', 'Vencida', 'Suspendida', 'Cancelada']);
  });

  it('pages through the results', async () => {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/tenants');
    await app.settle();
    app.backend
      .expectOne('/api/v1/platform/tenants?page=0&size=20')
      .flush({ ...TENANT_PAGE, totalItems: 45 });
    await app.settle();

    expect(byTestId(app.page(), 'range')?.textContent?.trim()).toBe('Mostrando 1–2 de 45');
    const next = Array.from(app.page().querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Siguiente',
    )!;
    next.click();
    await app.settle();
    app.backend
      .expectOne('/api/v1/platform/tenants?page=1&size=20')
      .flush({ ...TENANT_PAGE, page: 1, totalItems: 45 });
    await app.settle();
  });

  it('shows a Spanish message when the list fails', async () => {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/tenants');
    await app.settle();
    const { body, init } = problem(500, 'INTERNAL_ERROR');
    app.backend.expectOne('/api/v1/platform/tenants?page=0&size=20').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.INTERNAL_ERROR,
    );
    expectMigratedScreen(app.page());
  });

  it('offers onboarding with the plans permission', async () => {
    await open();
    expect(byTestId(app.page(), 'new-tenant')?.getAttribute('href')).toBe('/platform/tenants/new');
  });

  it('does not offer onboarding without the plans permission', async () => {
    await open(TENANTS_ONLY_ADMIN);
    expect(byTestId(app.page(), 'new-tenant')).toBeNull();
  });

  it('uses only the design system', async () => {
    await open();
    expectMigratedScreen(app.page());
  });

  it('is in the menu of the SUPER_ADMIN only', () => {
    expect(menuFor(PLATFORM_ADMIN).map((item) => item.label)).toContain('Clínicas');
    expect(menuFor(FULL_ACCOUNT).map((item) => item.label)).not.toContain('Clínicas');
  });

  it('is closed to clinic accounts, OWNER included (FR-001, FR-026)', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/platform/tenants');
    await app.settle();
    expect(app.router.url).toBe('/forbidden');
    app.backend.expectNone('/api/v1/platform/tenants?page=0&size=20');
  });
});
