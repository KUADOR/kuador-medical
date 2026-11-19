import { problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  submit,
  type,
} from '../../../../testing/app-harness';
import {
  SELF_REGISTRATION_DONE_MESSAGE,
  SELF_REGISTRATION_UNAVAILABLE_MESSAGE,
} from './self-registration.page';

const URL = '/api/v1/public/clinics/demo-clinic/patient-registrations';

describe('SelfRegistrationPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
    await app.harness.navigateByUrl('/clinics/demo-clinic/register');
    app.harness.detectChanges();
  });

  afterEach(() => app.backend.verify());

  function select(selector: string, value: string) {
    const element = app.page().querySelector<HTMLSelectElement>(selector)!;
    element.value = value;
    element.dispatchEvent(new Event('change'));
  }

  function fillValid() {
    type(app.page(), '#sr-documentNumber', ' 2222222222 ');
    type(app.page(), '#sr-firstNames', 'Alex');
    type(app.page(), '#sr-lastNames', 'Poe');
    type(app.page(), '#sr-birthDate', '1990-03-15');
    select('#sr-sex', 'FEMALE');
    type(app.page(), '#sr-email', 'alex@example.com');
    type(app.page(), '#sr-password', 'a-long-example-passphrase');
    type(app.page(), '#sr-confirmPassword', 'a-long-example-passphrase');
    const consent = app.page().querySelector<HTMLInputElement>('#sr-consent')!;
    consent.click();
  }

  it('requires the mandatory data and the consent, in Spanish', async () => {
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('Este campo es obligatorio.');
    expect(app.page().textContent).toContain(
      'Debes aceptar el tratamiento de tus datos personales para registrarte.',
    );
    app.backend.expectNone(URL);
  });

  it('rejects a birth date in the future and a phone without country code', async () => {
    fillValid();
    type(app.page(), '#sr-birthDate', '2999-01-01');
    type(app.page(), '#sr-phone', '0991234567');
    submit(app.page());
    await app.settle();

    expect(app.page().textContent).toContain('La fecha no puede ser posterior a hoy.');
    expect(app.page().textContent).toContain(
      'Escribe el teléfono con el signo + y el código de país',
    );
    app.backend.expectNone(URL);
  });

  it('sends the registration with consent, client id and Idempotency-Key', async () => {
    fillValid();
    submit(app.page());
    const request = app.backend.expectOne(URL);
    const body = request.request.body;
    expect(body).toMatchObject({
      documentType: 'NATIONAL_ID',
      documentNumber: '2222222222',
      firstNames: 'Alex',
      lastNames: 'Poe',
      birthDate: '1990-03-15',
      sex: 'FEMALE',
      email: 'alex@example.com',
      password: 'a-long-example-passphrase',
      consent: { textVersion: '2026-01' },
    });
    expect(body.phone).toBeUndefined();
    expect(body.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent?.trim()).toBe(
      SELF_REGISTRATION_DONE_MESSAGE,
    );
  });

  it('retries with the same Idempotency-Key after a network error', async () => {
    fillValid();
    submit(app.page());
    const first = app.backend.expectOne(URL);
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0 });
    await app.settle();

    submit(app.page());
    const second = app.backend.expectOne(URL);
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    expect(second.request.body.id).toBe(first.request.body.id);
    second.flush(null, { status: 202, statusText: 'Accepted' });
    await app.settle();
  });

  it('explains when the clinic has self-registration disabled', async () => {
    fillValid();
    submit(app.page());
    const { body, init } = problem(404, 'NOT_FOUND');
    app.backend.expectOne(URL).flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'unavailable')?.textContent?.trim()).toBe(
      SELF_REGISTRATION_UNAVAILABLE_MESSAGE,
    );
  });

  it('shows a minor birth date as a field error', async () => {
    fillValid();
    submit(app.page());
    const { body, init } = problem(400, 'VALIDATION', [
      { field: 'birthDate', code: 'MINOR_NOT_ALLOWED' },
    ]);
    app.backend.expectOne(URL).flush(body, init);
    await app.settle();

    expect(app.page().querySelector('#sr-birthDate')?.getAttribute('aria-invalid')).toBe('true');
    expect(app.page().textContent).toContain(
      'Solo las personas mayores de edad pueden registrarse por su cuenta.',
    );
  });
});
