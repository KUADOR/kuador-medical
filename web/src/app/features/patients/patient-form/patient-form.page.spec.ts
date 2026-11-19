import { TestRequest } from '@angular/common/http/testing';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import {
  ADULT_PATIENT,
  MINOR_PATIENT,
  PATIENTS_READER_ACCOUNT,
  RECEPTION_ACCOUNT,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../../testing/app-harness';

describe('PatientFormPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function openNew(account = RECEPTION_ACCOUNT) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/patients/new');
    await app.settle();
  }

  function select(selector: string, value: string): void {
    const element = app.page().querySelector<HTMLSelectElement>(selector);
    if (!element) {
      throw new Error(`No select ${selector}`);
    }
    element.value = value;
    element.dispatchEvent(new Event('change'));
  }

  function check(selector: string): void {
    app.page().querySelector<HTMLInputElement>(selector)?.click();
  }

  async function fillPatient(birthDate: string) {
    type(app.page(), '#patient-documentNumber', ' 2222222222 ');
    type(app.page(), '#patient-firstNames', 'Alex');
    type(app.page(), '#patient-lastNames', 'Poe');
    type(app.page(), '#patient-birthDate', birthDate);
    select('#patient-sex', 'FEMALE');
    await app.settle();
  }

  function expectRegister(): TestRequest {
    return app.backend.expectOne({ method: 'POST', url: '/api/v1/patients' });
  }

  describe('register', () => {
    it('sends the mandatory data, the consent and an Idempotency-Key', async () => {
      await openNew();
      await fillPatient('1990-03-15');
      check('#patient-consent');
      submit(app.page());

      const request = expectRegister();
      const key = request.request.headers.get('Idempotency-Key');
      expect(key).toMatch(/^[0-9a-f-]{36}$/);
      expect(request.request.body).toEqual({
        id: expect.stringMatching(/^[0-9a-f-]{36}$/),
        documentType: 'NATIONAL_ID',
        documentNumber: '2222222222',
        firstNames: 'Alex',
        lastNames: 'Poe',
        birthDate: '1990-03-15',
        sex: 'FEMALE',
        email: undefined,
        phone: undefined,
        address: undefined,
        consent: { textVersion: '2026-01', grantedBy: 'PATIENT' },
        guardian: undefined,
      });
      request.flush(ADULT_PATIENT, { status: 201, statusText: 'Created' });
      await app.settle();

      expect(app.router.url).toBe(`/patients/${ADULT_PATIENT.id}`);
      app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}`).flush(ADULT_PATIENT);
      await app.settle();
      app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}/guardian-links`).flush([]);
      await app.settle();
      expect(byTestId(app.page(), 'notice')?.textContent).toContain(
        'Se registró al paciente Alex Poe.',
      );
    });

    it('does not register without consent or mandatory data', async () => {
      await openNew();
      submit(app.page());
      await app.settle();

      expect(app.page().querySelector('#patient-documentNumber-error')?.textContent).toContain(
        'obligatorio',
      );
      expect(app.page().querySelector('#patient-birthDate-error')).not.toBeNull();
      expect(app.page().querySelector('#patient-consent-error')?.textContent).toContain(
        'consentimiento',
      );
      expect(document.activeElement?.id).toBe('patient-documentNumber');
      app.backend.expectNone('/api/v1/patients');
    });

    it('requires a legal guardian for a minor, whose consent comes from the guardian', async () => {
      await openNew();
      expect(byTestId(app.page(), 'guardian-section')).toBeNull();
      await fillPatient('2015-06-01');

      expect(byTestId(app.page(), 'guardian-section')).not.toBeNull();
      expect(app.page().querySelector('#patient-consentGrantedBy')).toBeNull();
      check('#patient-consent');
      submit(app.page());
      await app.settle();

      expect(app.page().querySelector('#guardian-relationship-error')?.textContent).toContain(
        'obligatorio',
      );
      expect(app.page().querySelector('#guardian-documentNumber-error')?.textContent).toContain(
        'obligatorio',
      );
      app.backend.expectNone('/api/v1/patients');

      type(app.page(), '#guardian-relationship', 'Madre');
      type(app.page(), '#guardian-documentNumber', '1111111111');
      type(app.page(), '#guardian-firstNames', 'Mary');
      type(app.page(), '#guardian-lastNames', 'Roe');
      type(app.page(), '#guardian-email', 'mary.roe@example.com');
      submit(app.page());

      const request = expectRegister();
      expect(request.request.body.consent).toEqual({
        textVersion: '2026-01',
        grantedBy: 'GUARDIAN',
      });
      expect(request.request.body.guardian).toEqual({
        relationship: 'Madre',
        person: {
          documentType: 'NATIONAL_ID',
          documentNumber: '1111111111',
          firstNames: 'Mary',
          lastNames: 'Roe',
          email: 'mary.roe@example.com',
          phone: undefined,
        },
      });
      request.flush(problem(500, 'INTERNAL_ERROR').body, problem(500, 'INTERNAL_ERROR').init);
      await app.settle();
    });

    it('keeps the same Idempotency-Key and id when retrying after a network error', async () => {
      await openNew();
      await fillPatient('1990-03-15');
      check('#patient-consent');
      submit(app.page());
      const first = expectRegister();
      first.error(new ProgressEvent('error'));
      await app.settle();
      expect(byTestId(app.page(), 'form-error')?.textContent).toContain('No pudimos conectar');

      submit(app.page());
      const retry = expectRegister();
      expect(retry.request.headers.get('Idempotency-Key')).toBe(
        first.request.headers.get('Idempotency-Key'),
      );
      expect(retry.request.body.id).toBe(first.request.body.id);

      const { body, init } = problem(400, 'VALIDATION', [{ field: 'sex', code: 'INVALID_FORMAT' }]);
      retry.flush(body, init);
      await app.settle();
      expect(app.page().querySelector('#patient-sex-error')?.textContent).toContain('formato');

      // The server answered: the corrected form is a new creation with a new key.
      select('#patient-sex', 'MALE');
      submit(app.page());
      const corrected = expectRegister();
      expect(corrected.request.headers.get('Idempotency-Key')).not.toBe(
        first.request.headers.get('Idempotency-Key'),
      );
      corrected.flush(problem(500, 'INTERNAL_ERROR').body, problem(500, 'INTERNAL_ERROR').init);
      await app.settle();
    });

    it('marks the document as duplicated in the clinic', async () => {
      await openNew();
      await fillPatient('1990-03-15');
      check('#patient-consent');
      submit(app.page());
      const { body, init } = problem(409, 'PATIENT_DUPLICATE');
      expectRegister().flush(body, init);
      await app.settle();

      expect(app.page().querySelector('#patient-documentNumber-error')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.PATIENT_DUPLICATE,
      );
      expect(document.activeElement?.id).toBe('patient-documentNumber');
    });

    it('places nested guardian errors of the API on the guardian fields', async () => {
      await openNew();
      await fillPatient('2015-06-01');
      check('#patient-consent');
      type(app.page(), '#guardian-relationship', 'Madre');
      type(app.page(), '#guardian-documentNumber', '1111111111');
      type(app.page(), '#guardian-firstNames', 'Mary');
      type(app.page(), '#guardian-lastNames', 'Roe');
      submit(app.page());
      const { body, init } = problem(400, 'VALIDATION', [
        { field: 'guardian.person.documentNumber', code: 'INVALID_FORMAT' },
      ]);
      expectRegister().flush(body, init);
      await app.settle();

      expect(app.page().querySelector('#guardian-documentNumber-error')?.textContent).toContain(
        'formato',
      );
    });

    it('is closed without patients.register', async () => {
      await signIn(app, PATIENTS_READER_ACCOUNT);
      await app.harness.navigateByUrl('/patients/new');
      expect(app.router.url).toBe('/forbidden');
    });
  });

  describe('edit', () => {
    async function openEdit(patient = ADULT_PATIENT) {
      await signIn(app, RECEPTION_ACCOUNT);
      await app.harness.navigateByUrl(`/patients/${patient.id}/edit`);
      app.backend.expectOne(`/api/v1/patients/${patient.id}`).flush(patient);
      await app.settle();
    }

    it('sends only the changed fields with the version read', async () => {
      await openEdit();
      expect(app.page().querySelector('h1')?.textContent).toContain('Editar paciente Alex Poe');
      expect(app.page().querySelector('#patient-consent')).toBeNull();
      type(app.page(), '#patient-phone', '+10000000000');
      submit(app.page());

      const request = app.backend.expectOne({
        method: 'PATCH',
        url: `/api/v1/patients/${ADULT_PATIENT.id}`,
      });
      expect(request.request.body).toEqual({ phone: '+10000000000', version: 2 });
      request.flush({ ...ADULT_PATIENT, phone: '+10000000000', version: 3 });
      await app.settle();
      expect(app.router.url).toBe(`/patients/${ADULT_PATIENT.id}`);
      app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}`).flush(ADULT_PATIENT);
      await app.settle();
      app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}/guardian-links`).flush([]);
      await app.settle();
    });

    it('offers to reload when someone else changed the patient', async () => {
      await openEdit(MINOR_PATIENT);
      type(app.page(), '#patient-firstNames', 'Johnny');
      submit(app.page());
      const { body, init } = problem(409, 'VERSION_CONFLICT');
      app.backend.expectOne(`/api/v1/patients/${MINOR_PATIENT.id}`).flush(body, init);
      await app.settle();

      const alert = byTestId(app.page(), 'form-error');
      expect(alert?.textContent).toContain('Otra persona modificó los datos de este paciente');
      Array.from(alert?.querySelectorAll('button') ?? [])
        .find((b) => b.textContent?.includes('Recargar'))
        ?.click();
      app.backend.expectOne(`/api/v1/patients/${MINOR_PATIENT.id}`).flush(MINOR_PATIENT);
      await app.settle();
      expect(byTestId(app.page(), 'form-error')).toBeNull();
    });
  });
});
