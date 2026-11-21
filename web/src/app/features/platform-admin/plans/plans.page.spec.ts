import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import {
  ARCHIVED_PLAN,
  FULL_ACCOUNT,
  PLATFORM_ADMIN,
  STANDARD_PLAN,
  SUPER_ADMIN,
  TRIAL_PLAN,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
} from '../../../../testing/app-harness';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';

describe('PlansPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(plans = [STANDARD_PLAN, ARCHIVED_PLAN, TRIAL_PLAN]) {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/plans');
    await app.settle();
    app.backend.expectOne('/api/v1/platform/plans').flush(plans);
    await app.settle();
  }

  function rows(): string[][] {
    return allByTestId(app.page(), 'plan-row').map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) =>
        (cell.textContent ?? '').replace(/\s+/g, ' ').trim(),
      ),
    );
  }

  it('lists the plans with their status and current prices in USD without VAT', async () => {
    await open();

    const table = rows();
    expect(table).toHaveLength(3);
    // Trial first, then active, then archived.
    expect(table[0][0]).toContain('Prueba');
    expect(table[0][0]).toContain('Plan de prueba');
    expect(table[0].slice(1, 5)).toEqual(['Activo', '3', 'Gratis', 'Gratis']);
    expect(table[1][0]).toContain('Estándar');
    expect(table[1][0]).toContain('STANDARD');
    expect(table[1][3]).toMatch(/^USD 49[,.]00$/);
    expect(table[1][4]).toMatch(/^USD 490[,.]00$/);
    expect(table[2][1]).toBe('Archivado');
    const headers = Array.from(app.page().querySelectorAll('th')).map((th) =>
      th.textContent?.trim(),
    );
    expect(headers).toContain('Mensual (sin IVA)');
    expect(headers).toContain('Anual (sin IVA)');
  });

  it('offers to archive only active paid plans', async () => {
    await open();
    const archiveButtons = Array.from(
      app.page().querySelectorAll<HTMLButtonElement>('[data-archive-plan]'),
    ).map((button) => button.getAttribute('data-archive-plan'));
    expect(archiveButtons).toEqual([STANDARD_PLAN.id]);
  });

  it('archives a plan after confirming and reloads the list', async () => {
    await open();
    buttonByText(app.page(), 'Archivar').click();
    await app.settle();
    expect(app.page().querySelector('[role="alertdialog"]')?.textContent).toContain(
      '¿Archivar el plan «Estándar»?',
    );

    byTestId(app.page(), 'confirm-archive')?.click();
    app.backend
      .expectOne({ method: 'POST', url: `/api/v1/platform/plans/${STANDARD_PLAN.id}/archive` })
      .flush({ ...STANDARD_PLAN, status: 'ARCHIVED' });
    await app.settle();
    app.backend
      .expectOne('/api/v1/platform/plans')
      .flush([{ ...STANDARD_PLAN, status: 'ARCHIVED' }]);
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe(
      'Se archivó el plan «Estándar».',
    );
    expect(app.page().querySelector('[role="alertdialog"]')).toBeNull();
    expect(rows()[0][1]).toBe('Archivado');
  });

  it('keeps the plan when the confirmation is cancelled', async () => {
    await open();
    buttonByText(app.page(), 'Archivar').click();
    await app.settle();
    buttonByText(app.page(), 'Cancelar').click();
    await app.settle();
    expect(app.page().querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('shows the Spanish message when archiving fails', async () => {
    await open();
    buttonByText(app.page(), 'Archivar').click();
    await app.settle();
    byTestId(app.page(), 'confirm-archive')?.click();
    const { body, init } = problem(400, 'VALIDATION', [
      { field: 'trial', code: 'TRIAL_PLAN_REQUIRED' },
    ]);
    app.backend.expectOne(`/api/v1/platform/plans/${STANDARD_PLAN.id}/archive`).flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'action-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.VALIDATION,
    );
  });

  it('filters by status through the API', async () => {
    await open();
    const select = byTestId(app.page(), 'plans-filter') as HTMLSelectElement;
    select.value = 'ARCHIVED';
    select.dispatchEvent(new Event('change'));
    await app.settle();
    const request = app.backend.expectOne((r) => r.url === '/api/v1/platform/plans');
    expect(request.request.params.get('status')).toBe('ARCHIVED');
    request.flush([ARCHIVED_PLAN]);
    await app.settle();
    expect(rows()).toHaveLength(1);
  });

  it('shows an empty state and a retry on failure', async () => {
    await open([]);
    expect(byTestId(app.page(), 'empty')).not.toBeNull();

    const select = byTestId(app.page(), 'plans-filter') as HTMLSelectElement;
    select.value = 'ACTIVE';
    select.dispatchEvent(new Event('change'));
    await app.settle();
    const { body, init } = problem(500, 'INTERNAL_ERROR');
    app.backend.expectOne((r) => r.url === '/api/v1/platform/plans').flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.INTERNAL_ERROR,
    );
    expectMigratedScreen(app.page());
  });

  it('uses only the design system with a single primary action', async () => {
    await open();
    expectMigratedScreen(app.page());
    expect(byTestId(app.page(), 'new-plan')?.getAttribute('href')).toBe('/platform/plans/new');
  });

  it('is closed to clinic accounts and to platform accounts without the permission', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/platform/plans');
    expect(app.router.url).toBe('/forbidden');

    app.session.clear();
    await signIn(app, SUPER_ADMIN);
    await app.harness.navigateByUrl('/platform/plans');
    expect(app.router.url).toBe('/forbidden');
  });
});
