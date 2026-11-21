import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { platformToday } from '../../../core/platform/plan-labels';
import {
  ARCHIVED_PLAN,
  LIMIT_CATALOG,
  MODULE_CATALOG,
  MONTHLY_PRICE,
  ANNUAL_PRICE,
  OLD_MONTHLY_PRICE,
  PLATFORM_ADMIN,
  STANDARD_PLAN,
  TRIAL_PLAN,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
  signIn,
  type,
} from '../../../../testing/app-harness';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';
import type { Plan, PlanPrice } from '../../../core/api/api-types';

describe('PlanFormPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function flushCatalogs() {
    app.backend.expectOne('/api/v1/platform/catalog/modules').flush(MODULE_CATALOG);
    app.backend.expectOne('/api/v1/platform/catalog/limits').flush(LIMIT_CATALOG);
  }

  async function openNew() {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/plans/new');
    await app.settle();
    await flushCatalogs();
    await app.settle();
  }

  async function openExisting(
    plan: Plan = STANDARD_PLAN,
    prices: PlanPrice[] = [MONTHLY_PRICE, ANNUAL_PRICE, OLD_MONTHLY_PRICE],
  ) {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl(`/platform/plans/${plan.id}`);
    await app.settle();
    await flushCatalogs();
    app.backend.expectOne(`/api/v1/platform/plans/${plan.id}`).flush(plan);
    app.backend.expectOne(`/api/v1/platform/plans/${plan.id}/prices`).flush(prices);
    await app.settle();
  }

  function input(selector: string): HTMLInputElement {
    const element = app.page().querySelector<HTMLInputElement>(selector);
    if (!element) {
      throw new Error(`No field ${selector}`);
    }
    return element;
  }

  function setNumber(selector: string, value: string): void {
    const element = input(selector);
    element.value = value;
    element.dispatchEvent(new Event('input'));
    element.dispatchEvent(new Event('blur'));
  }

  function checkModule(code: string): void {
    const box = (allByTestId(app.page(), 'module') as HTMLInputElement[]).find(
      (element) => element.value === code,
    );
    if (!box) {
      throw new Error(`No module ${code}`);
    }
    box.click();
  }

  function submitPlan(): void {
    app.page().querySelector('form.plan-form')?.dispatchEvent(new Event('submit'));
  }

  function fillNewPlan(): void {
    type(app.page(), '#plan-code', 'STANDARD');
    type(app.page(), '#plan-name', ' Estándar ');
    checkModule('AGENDA');
    checkModule('PATIENTS');
    setNumber('#limit-MAX_BRANCHES', '3');
    setNumber('#limit-MAX_USERS', '15');
    input('#limit-MAX_STORAGE_MB-unlimited').click();
    type(app.page(), '#price-MONTHLY', '49');
    type(app.page(), '#price-ANNUAL', '490,00');
  }

  it('creates a plan with modules, limits and monthly and annual prices in USD', async () => {
    await openNew();
    expect(
      (allByTestId(app.page(), 'module') as HTMLInputElement[]).map((box) => box.value),
    ).toEqual(['AGENDA', 'PATIENTS', 'ODONTOGRAM', 'E_INVOICING']);
    expect(allByTestId(app.page(), 'limit')).toHaveLength(3);

    fillNewPlan();
    await app.settle();
    expect(byTestId(app.page(), 'annual-saving')?.textContent).toContain('2 meses gratis');
    expect(input('#limit-MAX_STORAGE_MB').disabled).toBe(true);

    submitPlan();
    const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/platform/plans' });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    const { id, ...body } = request.request.body as Record<string, unknown>;
    expect(id).toEqual(expect.any(String));
    expect(body).toEqual({
      code: 'STANDARD',
      name: 'Estándar',
      description: null,
      trial: false,
      modules: ['AGENDA', 'PATIENTS'],
      limits: { MAX_BRANCHES: 3, MAX_USERS: 15, MAX_STORAGE_MB: null },
      prices: [
        { billingPeriod: 'MONTHLY', currency: 'USD', amount: '49.00' },
        { billingPeriod: 'ANNUAL', currency: 'USD', amount: '490.00' },
      ],
    });
    request.flush(STANDARD_PLAN);
    await app.settle();
    await app.settle();
    expect(app.router.url).toBe('/platform/plans');
    app.backend.expectOne('/api/v1/platform/plans').flush([STANDARD_PLAN]);
    await app.settle();
    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe('Se creó el plan «Estándar».');
  });

  it('does not send the plan until the required fields and prices are valid', async () => {
    await openNew();
    type(app.page(), '#plan-code', 'standard');
    type(app.page(), '#price-MONTHLY', '49.999');
    submitPlan();
    await app.settle();

    const errors = Array.from(app.page().querySelectorAll('.kd-field__error')).map((e) =>
      e.textContent?.trim(),
    );
    expect(errors).toContain(
      'Usa de 2 a 40 letras mayúsculas, números o guion bajo, empezando con una letra.',
    );
    expect(errors).toContain('Escribe un monto con hasta dos decimales, por ejemplo 49.00.');
    expect(errors).toContain('Este campo es obligatorio.');
    expect(document.activeElement?.getAttribute('aria-invalid')).toBe('true');
  });

  it('creates the trial plan without prices', async () => {
    await openNew();
    input('#plan-trial').click();
    await app.settle();
    expect(app.page().querySelector('#price-MONTHLY')).toBeNull();

    type(app.page(), '#plan-code', 'TRIAL');
    type(app.page(), '#plan-name', 'Prueba');
    for (const code of ['MAX_BRANCHES', 'MAX_USERS', 'MAX_STORAGE_MB']) {
      setNumber(`#limit-${code}`, '1');
    }
    submitPlan();
    const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/platform/plans' });
    expect(request.request.body.trial).toBe(true);
    expect(request.request.body.prices).toEqual([]);
    const { body, init } = problem(409, 'VALIDATION', [{ field: 'trial', code: 'DUPLICATE' }]);
    request.flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(ERROR_MESSAGES.VALIDATION);
    expect(app.page().textContent).toContain('Este valor ya está registrado.');
  });

  it('edits a plan with its version, keeping code, trial flag and prices out of the update', async () => {
    await openExisting();
    expect(app.page().querySelector('h1')?.textContent?.trim()).toBe('Editar plan Estándar');
    expect(input('#plan-code').disabled).toBe(true);
    expect(input('#plan-code').value).toBe('STANDARD');
    expect(app.page().querySelector('#plan-trial')).toBeNull();
    expect(app.page().querySelector('#price-MONTHLY')).toBeNull();
    expect(input('#limit-MAX_STORAGE_MB-unlimited').checked).toBe(true);
    expect(input('#limit-MAX_USERS').value).toBe('15');

    setNumber('#limit-MAX_USERS', '20');
    checkModule('ODONTOGRAM');
    app.page().querySelector('form.plan-form')?.dispatchEvent(new Event('submit'));
    const request = app.backend.expectOne({
      method: 'PUT',
      url: `/api/v1/platform/plans/${STANDARD_PLAN.id}`,
    });
    expect(request.request.body).toEqual({
      name: 'Estándar',
      description: 'Clínica médica con hasta tres sedes.',
      modules: ['AGENDA', 'PATIENTS', 'ODONTOGRAM'],
      limits: { MAX_BRANCHES: 3, MAX_USERS: 20, MAX_STORAGE_MB: null },
      version: 3,
    });
    request.flush({ ...STANDARD_PLAN, version: 4 });
    await app.settle();
    expect(app.router.url).toBe('/platform/plans');
    app.backend.expectOne('/api/v1/platform/plans').flush([STANDARD_PLAN]);
    await app.settle();
  });

  it('offers to reload when another person changed the plan', async () => {
    await openExisting();
    submitPlan();
    const { body, init } = problem(409, 'VERSION_CONFLICT');
    app.backend.expectOne(`/api/v1/platform/plans/${STANDARD_PLAN.id}`).flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'form-error')?.textContent).toContain(
      ERROR_MESSAGES.VERSION_CONFLICT,
    );
    expect(app.page().textContent).toContain('Recargar el plan');
  });

  it('shows the price history and publishes a new immutable version', async () => {
    await openExisting();
    const history = allByTestId(app.page(), 'price-row').map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim()),
    );
    expect(history).toHaveLength(3);
    expect(history.map((row) => row[3])).toEqual(['Vigente', 'Vigente', 'Anterior']);
    expect(history[2][1]).toMatch(/^USD 39[,.]00$/);
    expect(input('#new-price-from').value).toBe(platformToday());

    type(app.page(), '#new-price-amount', '55');
    const from = input('#new-price-from');
    from.value = '2999-03-01';
    from.dispatchEvent(new Event('input'));
    app.page().querySelector('form.price-form')?.dispatchEvent(new Event('submit'));

    const request = app.backend.expectOne({
      method: 'POST',
      url: `/api/v1/platform/plans/${STANDARD_PLAN.id}/prices`,
    });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(request.request.body).toEqual({
      id: expect.any(String),
      billingPeriod: 'MONTHLY',
      currency: 'USD',
      amount: '55.00',
      effectiveFrom: '2999-03-01T05:00:00Z',
    });
    const created: PlanPrice = {
      ...MONTHLY_PRICE,
      id: '00000000-0000-0000-0000-0000000000d9',
      amount: '55.00',
      effectiveFrom: '2999-03-01T05:00:00Z',
    };
    request.flush(created);
    await app.settle();
    app.backend.expectOne(`/api/v1/platform/plans/${STANDARD_PLAN.id}`).flush(STANDARD_PLAN);
    app.backend
      .expectOne(`/api/v1/platform/plans/${STANDARD_PLAN.id}/prices`)
      .flush([created, MONTHLY_PRICE, ANNUAL_PRICE, OLD_MONTHLY_PRICE]);
    await app.settle();

    expect(byTestId(app.page(), 'price-notice')?.textContent).toContain(
      'Se publicó la tarifa mensual de USD 55',
    );
    const states = allByTestId(app.page(), 'price-row').map((row) =>
      row.querySelectorAll('td')[3].textContent?.trim(),
    );
    expect(states[0]).toBe('Programada');
    expect(input('#new-price-amount').value).toBe('');
  });

  it('rejects a price with a past start date before calling the API', async () => {
    await openExisting();
    type(app.page(), '#new-price-amount', '55');
    const from = input('#new-price-from');
    from.value = '2000-01-01';
    from.dispatchEvent(new Event('input'));
    app.page().querySelector('form.price-form')?.dispatchEvent(new Event('submit'));
    await app.settle();
    expect(app.page().textContent).toContain('La fecha no puede ser anterior a hoy.');
  });

  it('opens an archived plan read-only, without publishing prices', async () => {
    await openExisting(ARCHIVED_PLAN, [MONTHLY_PRICE]);
    expect(app.page().querySelector('h1')?.textContent?.trim()).toBe('Plan Anterior');
    expect(byTestId(app.page(), 'read-only')).not.toBeNull();
    expect(input('#plan-name').disabled).toBe(true);
    expect(
      (allByTestId(app.page(), 'module') as HTMLInputElement[]).every((box) => box.disabled),
    ).toBe(true);
    expect(app.page().querySelector('form.price-form')).toBeNull();
    expect(app.page().querySelector('button[type="submit"]')).toBeNull();
    expect(byTestId(app.page(), 'prices')).not.toBeNull();
  });

  it('shows the trial plan without prices section', async () => {
    await openExisting(TRIAL_PLAN, []);
    expect(byTestId(app.page(), 'trial-badge')).not.toBeNull();
    expect(byTestId(app.page(), 'prices')).toBeNull();
  });

  it('shows not found without retry', async () => {
    await signIn(app, PLATFORM_ADMIN);
    await app.harness.navigateByUrl('/platform/plans/missing');
    await app.settle();
    await flushCatalogs();
    const { body, init } = problem(404, 'NOT_FOUND');
    app.backend.expectOne('/api/v1/platform/plans/missing').flush(body, init);
    // forkJoin cancels the pending price history when the plan fails.
    app.backend.match('/api/v1/platform/plans/missing/prices');
    await app.settle();
    expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(ERROR_MESSAGES.NOT_FOUND);
    expect(app.page().textContent).not.toContain('Volver a intentar');
  });

  it('uses only the design system with a single primary action', async () => {
    await openExisting();
    expectMigratedScreen(app.page());
  });
});
