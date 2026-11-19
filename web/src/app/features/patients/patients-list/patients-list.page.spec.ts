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
    expect(byTestId(rows[1], 'minor-badge')).not.toBeNull();
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe(`/patients/${ADULT_PATIENT.id}`);
    expect(byTestId(app.page(), 'new-patient')).not.toBeNull();
    expect(byTestId(app.page(), 'settings-link')).not.toBeNull();
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
    expect(byTestId(app.page(), 'load-error')?.textContent).toContain('No pudimos conectar');

    buttonByText(app.page(), 'Volver a intentar').click();
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
