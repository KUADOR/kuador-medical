import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { FULL_ACCOUNT, problem } from '../../../testing/fixtures';
import {
  AppHarness,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
  submit,
} from '../../../testing/app-harness';
import { expectNoAxeViolations } from '../../../testing/axe';
import { expectMigratedScreen } from '../../../testing/migrated-screen';
import {
  OWNER_ACCOUNT,
  PENDING_EXPORT,
  READY_EXPORT,
  SUSPENDED_SUBSCRIPTION,
} from '../../../testing/owner-fixtures';
import { menuFor } from '../../core/session/navigation';
import { SubscriptionService } from '../../core/subscription/subscription.service';
import { EXPORT_POLL_INTERVAL } from './tenant-export.page';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

describe('TenantExportPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [{ provide: EXPORT_POLL_INTERVAL, useValue: 0 }] });
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(account = OWNER_ACCOUNT) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/data-export');
    app.harness.detectChanges();
  }

  function acceptResponsibility() {
    app.page().querySelector<HTMLInputElement>('#export-responsibility')?.click();
  }

  async function requestExport(format: 'JSON' | 'CSV' = 'JSON') {
    const select = app.page().querySelector<HTMLSelectElement>('#export-format');
    if (select) {
      select.value = format;
      select.dispatchEvent(new Event('change'));
    }
    acceptResponsibility();
    submit(app.page());
    const request = app.backend.expectOne('/api/v1/tenant/exports');
    return request;
  }

  it('is offered in the menu to the OWNER', () => {
    expect(menuFor(OWNER_ACCOUNT).map((item) => item.label)).toContain('Exportar datos');
    expect(menuFor(FULL_ACCOUNT).map((item) => item.label)).not.toContain('Exportar datos');
  });

  it('is closed to accounts without tenant.data.export', async () => {
    await open(FULL_ACCOUNT);
    expect(app.router.url).toBe('/forbidden');
  });

  it('asks the OWNER to accept that the clinic is responsible for the data first', async () => {
    await open();
    submit(app.page());
    await app.settle();

    app.backend.expectNone('/api/v1/tenant/exports');
    expect(app.page().querySelector('#export-responsibility')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
    expect(app.page().textContent).toContain('Kuador es solo el encargado de su tratamiento');
    expectMigratedScreen(app.page());
    await expectNoAxeViolations(app.page());
  });

  it('requests the export with a client id as idempotency key and shows the download when ready', async () => {
    await open();
    const request = await requestExport('CSV');

    const body = request.request.body as { id: string; format: string };
    expect(body.format).toBe('CSV');
    expect(body.id).toMatch(UUID);
    expect(request.request.headers.get('Idempotency-Key')).toBe(body.id);
    request.flush({ ...PENDING_EXPORT, format: 'CSV' });
    await app.settle();
    expect(byTestId(app.page(), 'export-pending')).not.toBeNull();

    // While PENDING, the page checks the status by itself (interval 0 in the tests).
    await app.settle();
    app.backend.expectOne(`/api/v1/tenant/exports/${PENDING_EXPORT.id}`).flush(PENDING_EXPORT);
    await app.settle();
    await app.settle();
    app.backend.expectOne(`/api/v1/tenant/exports/${PENDING_EXPORT.id}`).flush(READY_EXPORT);
    await app.settle();

    expect(byTestId(app.page(), 'export-ready')?.textContent).toContain('8 de julio de 2030');
    expect(byTestId(app.page(), 'export-download')?.getAttribute('href')).toBe(
      READY_EXPORT.downloadUrl,
    );
    expectMigratedScreen(app.page());
    app.backend.expectNone(`/api/v1/tenant/exports/${PENDING_EXPORT.id}`);

    buttonByText(app.page(), 'Nueva exportación').click();
    await app.settle();
    expect(app.page().querySelector<HTMLInputElement>('#export-responsibility')?.checked).toBe(
      false,
    );
  });

  it('explains a failed export and lets the OWNER try again', async () => {
    await open();
    const request = await requestExport();
    request.flush({ ...PENDING_EXPORT, status: 'FAILED' });
    await app.settle();

    expect(byTestId(app.page(), 'export-failed')?.textContent).toContain(
      'No se pudo generar la exportación',
    );
    buttonByText(app.page(), 'Intentar de nuevo').click();
    await app.settle();
    expect(app.page().querySelector('form')).not.toBeNull();
  });

  it('shows the Spanish message of a rejected request', async () => {
    await open();
    const request = await requestExport();
    const { body, init } = problem(403, 'FORBIDDEN');
    request.flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(ERROR_MESSAGES.FORBIDDEN);
  });

  it('reminds a read-only clinic that exporting is still allowed', async () => {
    const subscriptions = TestBed.inject(SubscriptionService);
    await open();
    const loading = subscriptions.load();
    app.backend.expectOne('/api/v1/tenant/subscription').flush(SUSPENDED_SUBSCRIPTION);
    await loading;
    await app.settle();

    expect(byTestId(app.page(), 'read-only-info')?.textContent).toContain(
      'puedes exportar sus datos',
    );
  });
});
