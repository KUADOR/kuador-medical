import { FULL_ACCOUNT, problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../../testing/app-harness';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';
import {
  ACTIVE_SUBSCRIPTION,
  PLANS,
  TENANTS_ADMIN,
  STANDARD_PLAN_ID,
  TENANT,
  TENANTS_ONLY_ADMIN,
  TENANT_ID,
} from '../../../../testing/platform-tenant-fixtures';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';

describe('TenantFormPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open() {
    await signIn(app, TENANTS_ADMIN);
    await app.harness.navigateByUrl('/platform/tenants/new');
    await app.settle();
    app.backend.expectOne('/api/v1/platform/plans?status=ACTIVE').flush(PLANS);
    await app.settle();
  }

  function select(selector: string, value: string): void {
    const element = app.page().querySelector(selector) as HTMLSelectElement;
    element.value = value;
    element.dispatchEvent(new Event('change'));
  }

  function fillAll(): void {
    type(app.page(), '#tenant-legal-name', ' Example Clinic S.A. ');
    type(app.page(), '#tenant-ruc', '0000000000001');
    select('#tenant-type', 'MEDICAL');
    type(app.page(), '#tenant-city', 'Quito');
    type(app.page(), '#tenant-address', '123 Example Avenue');
    type(app.page(), '#tenant-phone', '+593000000000');
    type(app.page(), '#tenant-contact-email', 'contact@example.com');
    select('#tenant-plan', STANDARD_PLAN_ID);
    select('#tenant-period', 'ANNUAL');
    type(app.page(), '#tenant-owner-name', 'Jane Doe');
    type(app.page(), '#tenant-owner-email', 'jane.doe@example.com');
  }

  function summaryItems(): string[] {
    return Array.from(
      app.page().querySelectorAll('kd-error-summary li'),
      (item) => item.textContent?.trim() ?? '',
    );
  }

  it('offers the active plans with their prices without VAT', async () => {
    await open();
    const labels = Array.from(
      (app.page().querySelector('#tenant-plan') as HTMLSelectElement).options,
      (option) => option.textContent?.trim(),
    );
    expect(labels).toEqual([
      'Elige un plan',
      'Prueba · prueba sin costo',
      'Standard · Mensual 49,00 USD · Anual 490,00 USD',
      'Premium · Mensual 99,00 USD',
    ]);
    expect(
      (app.page().querySelector('#tenant-country') as HTMLSelectElement).selectedOptions[0]
        .textContent,
    ).toBe('Ecuador');
  });

  it('rejects the onboarding without sending it and names every missing field (FR-007, CA 4)', async () => {
    await open();
    submit(app.page());
    await app.settle();

    expect(summaryItems()).toEqual([
      'Razón social: este campo es obligatorio.',
      'RUC: este campo es obligatorio.',
      'Tipo de clínica: este campo es obligatorio.',
      'Ciudad: este campo es obligatorio.',
      'Dirección: este campo es obligatorio.',
      'Teléfono: este campo es obligatorio.',
      'Correo de contacto: este campo es obligatorio.',
      'Plan: este campo es obligatorio.',
      'Nombre del OWNER: este campo es obligatorio.',
      'Correo del OWNER: este campo es obligatorio.',
    ]);
    // Each item links to its field.
    expect(app.page().querySelector('kd-error-summary a')?.getAttribute('href')).toBe(
      '#tenant-legal-name',
    );
    expect(app.page().querySelector('#tenant-legal-name')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
    app.backend.expectNone('/api/v1/platform/tenants');
  });

  it('checks the RUC format and the emails before sending', async () => {
    await open();
    fillAll();
    type(app.page(), '#tenant-ruc', '12345');
    type(app.page(), '#tenant-owner-email', 'not-an-email');
    submit(app.page());
    await app.settle();

    expect(summaryItems()).toEqual([
      'RUC: debe tener exactamente 13 dígitos.',
      'Correo del OWNER: escribe un correo válido, por ejemplo nombre@clinica.com.',
    ]);
    expect(app.page().textContent).toContain('El RUC debe tener exactamente 13 dígitos.');
    app.backend.expectNone('/api/v1/platform/tenants');
  });

  it('onboards the clinic once and opens its record with a notice', async () => {
    await open();
    fillAll();
    submit(app.page());
    await app.settle();

    const request = app.backend.expectOne('/api/v1/platform/tenants');
    expect(request.request.method).toBe('POST');
    const { id, ...body } = request.request.body;
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(body).toEqual({
      legalName: 'Example Clinic S.A.',
      tradeName: null,
      ruc: '0000000000001',
      clinicType: 'MEDICAL',
      countryCode: 'EC',
      city: 'Quito',
      address: '123 Example Avenue',
      phone: '+593000000000',
      contactEmail: 'contact@example.com',
      mspEstablishmentNumber: null,
      planId: STANDARD_PLAN_ID,
      billingPeriod: 'ANNUAL',
      owner: { fullName: 'Jane Doe', email: 'jane.doe@example.com' },
    });
    request.flush(TENANT, { status: 201, statusText: 'Created' });
    await app.settle();

    expect(app.router.url).toBe(`/platform/tenants/${TENANT_ID}`);
    app.backend.expectOne(`/api/v1/platform/tenants/${TENANT_ID}`).flush(TENANT);
    app.backend
      .expectOne(`/api/v1/platform/tenants/${TENANT_ID}/subscription`)
      .flush(ACTIVE_SUBSCRIPTION);
    await app.settle();
    // The plan change panel of the record reads the plans.
    app.backend.expectOne('/api/v1/platform/plans?status=ACTIVE').flush(PLANS);
    await app.settle();
    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe(
      'Se registró Example Clinic y enviamos la invitación a su OWNER (j***@example.com).',
    );
  });

  it('marks the RUC when it is already registered (CA 4)', async () => {
    await open();
    fillAll();
    submit(app.page());
    await app.settle();
    const { body, init } = problem(409, 'RUC_ALREADY_REGISTERED');
    app.backend.expectOne('/api/v1/platform/tenants').flush(body, init);
    await app.settle();

    expect(summaryItems()).toEqual(['RUC: ya existe una clínica registrada con ese RUC.']);
    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.RUC_ALREADY_REGISTERED,
    );
    expect(app.page().querySelector('#tenant-ruc')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('puts the field errors of the api on their fields', async () => {
    await open();
    fillAll();
    submit(app.page());
    await app.settle();
    const { body, init } = problem(400, 'VALIDATION', [{ field: 'owner.email', code: 'REQUIRED' }]);
    app.backend.expectOne('/api/v1/platform/tenants').flush(body, init);
    await app.settle();

    expect(summaryItems()).toEqual(['Correo del OWNER: este campo es obligatorio.']);
  });

  it('retries with the same idempotency key after a network failure', async () => {
    await open();
    fillAll();
    submit(app.page());
    await app.settle();
    const first = app.backend.expectOne('/api/v1/platform/tenants');
    const key = first.request.headers.get('Idempotency-Key');
    const id = first.request.body.id;
    first.error(new ProgressEvent('error'), { status: 0 });
    await app.settle();

    submit(app.page());
    await app.settle();
    const second = app.backend.expectOne('/api/v1/platform/tenants');
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    expect(second.request.body.id).toBe(id);
    second.flush(TENANT, { status: 201, statusText: 'Created' });
    await app.settle();
    app.backend.expectOne(`/api/v1/platform/tenants/${TENANT_ID}`).flush(TENANT);
    app.backend
      .expectOne(`/api/v1/platform/tenants/${TENANT_ID}/subscription`)
      .flush(ACTIVE_SUBSCRIPTION);
    await app.settle();
    // The plan change panel of the record reads the plans.
    app.backend.expectOne('/api/v1/platform/plans?status=ACTIVE').flush(PLANS);
    await app.settle();
  });

  it('uses only the design system, with one primary action', async () => {
    await open();
    expectMigratedScreen(app.page());
  });

  it('needs the plans permission and is closed to clinic accounts', async () => {
    await signIn(app, TENANTS_ONLY_ADMIN);
    await app.harness.navigateByUrl('/platform/tenants/new');
    await app.settle();
    expect(app.router.url).toBe('/forbidden');

    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/platform/tenants/new');
    await app.settle();
    expect(app.router.url).toBe('/forbidden');
  });
});
