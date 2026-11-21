import {
  ENDED_AT_ADULTHOOD_LINK,
  PATIENT_ACCOUNT,
  RECEPTION_ACCOUNT,
  problem,
} from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../../testing/app-harness';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import type { GuardianLink } from '../../../core/api/api-types';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';
import { knownPeople } from './my-guardian-links.page';

describe('MyGuardianLinksPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(links: GuardianLink[] = [ENDED_AT_ADULTHOOD_LINK]) {
    await signIn(app, PATIENT_ACCOUNT);
    await app.harness.navigateByUrl('/my-contacts');
    app.backend.expectOne('/api/v1/me/guardian-links').flush(links);
    await app.settle();
  }

  /** Radio button by the text of its label (RadioControlValueAccessor keeps the value). */
  function radio(label: string): HTMLInputElement {
    const match = Array.from(app.page().querySelectorAll('label')).find((element) =>
      element.textContent?.trim().startsWith(label),
    );
    const input = match?.htmlFor
      ? app.page().querySelector<HTMLInputElement>(`input[type="radio"][id="${match.htmlFor}"]`)
      : null;
    if (!input) {
      throw new Error(`No radio ${label}`);
    }
    return input;
  }

  it('shows the link ended at adulthood as history', async () => {
    await open();
    const past = allByTestId(app.page(), 'past-link');
    expect(past).toHaveLength(1);
    expect(past[0].textContent).toContain('Mayoría de edad del paciente');
    expect(byTestId(app.page(), 'no-current')).not.toBeNull();
  });

  it('authorizes the former guardian as emergency contact with an Idempotency-Key', async () => {
    await open();
    expect(radio('Mary Roe').checked).toBe(true);
    expect(byTestId(app.page(), 'new-person')).toBeNull();
    expect(app.page().querySelector<HTMLInputElement>('#authorized-relationship')?.value).toBe(
      'Madre',
    );

    radio('Contacto de emergencia').click();
    submit(app.page());
    const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/me/guardian-links' });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.body).toEqual({
      id: expect.any(String),
      linkType: 'EMERGENCY_CONTACT',
      relationship: 'Madre',
      guardianId: ENDED_AT_ADULTHOOD_LINK.guardian.id,
    });
    const authorized: GuardianLink = {
      ...ENDED_AT_ADULTHOOD_LINK,
      id: 'new',
      linkType: 'EMERGENCY_CONTACT',
      current: true,
      validTo: null,
      endReason: null,
      validFrom: '2033-06-02T00:00:00Z',
      authorizedBy: PATIENT_ACCOUNT.accountId,
    };
    request.flush(authorized, { status: 201, statusText: 'Created' });
    app.backend.expectOne('/api/v1/me/guardian-links').flush([authorized, ENDED_AT_ADULTHOOD_LINK]);
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent).toContain(
      'Autorizaste a Mary Roe como contacto de emergencia.',
    );
    const current = allByTestId(app.page(), 'current-link');
    expect(current).toHaveLength(1);
    expect(current[0].textContent).toContain('Autorizado por el paciente');
  });

  it('asks what is authorized before calling the API', async () => {
    await open();
    submit(app.page());
    await app.settle();
    expect(app.page().querySelector('#linkType-error')?.textContent).toContain('Elige');
    app.backend.expectNone('/api/v1/me/guardian-links');
  });

  it('authorizes someone new with delegated access', async () => {
    await open([]);
    expect(radio('Otra persona').checked).toBe(true);
    type(app.page(), '#authorized-documentNumber', '4444444444');
    type(app.page(), '#authorized-firstNames', 'Sam');
    type(app.page(), '#authorized-lastNames', 'Lee');
    type(app.page(), '#authorized-relationship', 'Pareja');
    radio('Acceso delegado').click();
    submit(app.page());

    const request = app.backend.expectOne('/api/v1/me/guardian-links');
    expect(request.request.body).toEqual({
      id: expect.any(String),
      linkType: 'DELEGATED_ACCESS',
      relationship: 'Pareja',
      person: {
        documentType: 'NATIONAL_ID',
        documentNumber: '4444444444',
        firstNames: 'Sam',
        lastNames: 'Lee',
        email: undefined,
        phone: undefined,
      },
    });
    const { body, init } = problem(403, 'FORBIDDEN');
    request.flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'form-error')?.textContent?.trim()).toBe(ERROR_MESSAGES.FORBIDDEN);
  });


  it('uses only the design system (MEDK-44)', async () => {
    await open();
    expectMigratedScreen(app.page());

    submit(app.page());
    await app.settle();
    expectMigratedScreen(app.page());
    const group = app.page().querySelector('fieldset[aria-invalid="true"]');
    expect(group?.getAttribute('aria-describedby')).toBe('linkType-error');
    expect(document.activeElement).toBe(radio('Contacto de emergencia'));
  });

  it('uses only the design system for someone new (MEDK-44)', async () => {
    await open([]);
    submit(app.page());
    await app.settle();

    expectMigratedScreen(app.page());
    const number = app.page().querySelector('#authorized-documentNumber');
    expect(app.page().querySelector('label[for="authorized-documentNumber"]')).not.toBeNull();
    expect(number?.getAttribute('aria-invalid')).toBe('true');
    expect(number?.getAttribute('aria-describedby')).toBe('authorized-documentNumber-error');
    expect(app.page().querySelector('#authorized-phone')?.getAttribute('aria-describedby')).toBe(
      'authorized-phone-hint',
    );
  });

  it('is only for patient accounts', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl('/my-contacts');
    expect(app.router.url).toBe('/forbidden');
  });

  it('lists each person of the history once, newest link first', () => {
    const older = { ...ENDED_AT_ADULTHOOD_LINK, id: 'old', validFrom: '2020-01-01T00:00:00Z' };
    const other = {
      ...ENDED_AT_ADULTHOOD_LINK,
      id: 'other',
      guardian: { id: 'g2', firstNames: 'Paul', lastNames: 'Roe' },
      relationship: 'Padre',
      validFrom: '2025-01-01T00:00:00Z',
    };
    expect(knownPeople([older, other, ENDED_AT_ADULTHOOD_LINK]).map((p) => p.name)).toEqual([
      'Mary Roe',
      'Paul Roe',
    ]);
  });
});
