import { TestBed } from '@angular/core/testing';
import { NoticeService } from '../../../core/notice/notice.service';
import {
  ACCOUNT,
  ADULT_PATIENT,
  MINOR_PATIENT,
  PATIENTS_READER_ACCOUNT,
  RECEPTION_ACCOUNT,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../../testing/app-harness';
import type { Patient } from '../../../core/api/api-types';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { expectDesignSystemScreen, primaryActions } from '../../../../testing/screen-rules';

describe('PatientsListPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  function page(items: Patient[], totalItems = items.length, index = 0) {
    return { items, page: index, size: 20, totalItems };
  }

  async function open(account = RECEPTION_ACCOUNT, items = [ADULT_PATIENT, MINOR_PATIENT]) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/patients');
    app.backend.expectOne('/api/v1/patients?page=0&size=20').flush(page(items));
    await app.settle();
  }

  it('lists the patients of the clinic, marking minors', async () => {
    await open();

    const rows = allByTestId(app.page(), 'patient');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Alex Poe');
    expect(rows[0].textContent).toContain('Sin cuenta');
    expect(rows[0].textContent).toContain('2222222222');
    expect(rows[0].textContent).toContain('Nacimiento 15 mar 1990');
    expect(rows[0].textContent).not.toContain('Menor de edad');
    expect(rows[1].textContent).toContain('Menor de edad');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe(`/patients/${ADULT_PATIENT.id}`);
    expect(byTestId(app.page(), 'new-patient')).not.toBeNull();
    expect(byTestId(app.page(), 'settings-link')).not.toBeNull();
  });

  it('is built with the design system; Buscar is the one primary action', async () => {
    await open();
    expectDesignSystemScreen(app.page());
    expect(primaryActions(app.page()).map((b) => b.textContent?.trim())).toEqual(['Buscar']);
    expect(
      app.page().querySelector('kd-list[aria-label="Pacientes de la clínica"]'),
    ).not.toBeNull();
    await expectNoAxeViolations(app.page());
  });

  it('shows the loading state of the library while the search runs', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl('/patients');
    app.harness.detectChanges();

    const loading = app.page().querySelector('kd-loading-state');
    expect(loading?.getAttribute('role')).toBe('status');
    expect(loading?.textContent).toContain('Buscando pacientes…');
    await expectNoAxeViolations(app.page());
    app.backend.expectOne('/api/v1/patients?page=0&size=20').flush(page([]));
  });

  it('shows the empty state of the library when the clinic has no patients', async () => {
    await open(RECEPTION_ACCOUNT, []);
    const empty = byTestId(app.page(), 'empty');
    expect(empty?.tagName.toLowerCase()).toBe('kd-empty-state');
    expect(empty?.textContent).toContain('todavía no tiene pacientes');
    expectDesignSystemScreen(app.page());
    await expectNoAxeViolations(app.page());
  });

  it('finds a patient and opens the record in 3 actions (baseline F2)', async () => {
    await open();
    let actions = 0;
    type(app.page(), '#patients-q', 'Poe');
    actions++;
    buttonByText(app.page(), 'Buscar').click();
    actions++;
    app.backend.expectOne('/api/v1/patients?q=Poe&page=0&size=20').flush(page([ADULT_PATIENT]));
    await app.settle();
    allByTestId(app.page(), 'patient')[0].querySelector('a')!.click();
    actions++;
    await app.settle();

    expect(actions).toBeLessThanOrEqual(3);
    expect(app.router.url).toBe(`/patients/${ADULT_PATIENT.id}`);
    app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}`).flush(ADULT_PATIENT);
    app.backend.match(() => true).forEach((request) => request.flush([]));
  });

  it('searches by name or document from the first page', async () => {
    await open();
    type(app.page(), '#patients-q', ' Poe ');
    submit(app.page());
    app.backend.expectOne('/api/v1/patients?q=Poe&page=0&size=20').flush(page([]));
    await app.settle();

    expect(byTestId(app.page(), 'empty')?.textContent).toContain(
      'No hay pacientes que coincidan con «Poe».',
    );
  });

  it('pages through the results', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl('/patients');
    app.backend.expectOne('/api/v1/patients?page=0&size=20').flush(page([ADULT_PATIENT], 45));
    await app.settle();
    expect(byTestId(app.page(), 'page-label')?.textContent).toContain('Página 1 de 3');

    buttonByText(app.page(), 'Siguiente').click();
    app.backend.expectOne('/api/v1/patients?page=1&size=20').flush(page([MINOR_PATIENT], 45, 1));
    await app.settle();
    expect(byTestId(app.page(), 'page-label')?.textContent).toContain('Página 2 de 3');
  });

  it('hides registration without patients.register and shows the notice of the form', async () => {
    TestBed.inject(NoticeService).show('Se registró al paciente Alex Poe.');
    await open(PATIENTS_READER_ACCOUNT);
    expect(byTestId(app.page(), 'new-patient')).toBeNull();
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se registró al paciente');
  });

  it('shows a Spanish error and retries when the list fails', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl('/patients');
    app.backend.expectOne('/api/v1/patients?page=0&size=20').error(new ProgressEvent('error'));
    await app.settle();
    expect(byTestId(app.page(), 'load-error')?.getAttribute('role')).toBe('alert');
    expect(byTestId(app.page(), 'load-error')?.textContent).toContain('No pudimos conectar');
    expectDesignSystemScreen(app.page());
    await expectNoAxeViolations(app.page());

    buttonByText(app.page(), 'Reintentar').click();
    app.backend.expectOne('/api/v1/patients?page=0&size=20').flush(page([]));
    await app.settle();
    expect(byTestId(app.page(), 'empty')?.textContent).toContain('todavía no tiene pacientes');
  });

  it('is closed without patients.read or when the plan does not enable the module', async () => {
    await signIn(app, ACCOUNT);
    await app.harness.navigateByUrl('/patients');
    expect(app.router.url).toBe('/forbidden');
  });

  it('is closed when the plan does not enable the patients module', async () => {
    await signIn(app, { ...RECEPTION_ACCOUNT, enabledModules: ['identity'] });
    await app.harness.navigateByUrl('/patients');
    expect(app.router.url).toBe('/forbidden');
  });
});
