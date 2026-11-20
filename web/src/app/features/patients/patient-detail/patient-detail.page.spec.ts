import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import {
  ACCOUNT,
  ADULT_PATIENT,
  CURRENT_GUARDIAN_LINK,
  ENDED_AT_ADULTHOOD_LINK,
  MINOR_PATIENT,
  PATIENTS_READER_ACCOUNT,
  RECEPTION_ACCOUNT,
  problem,
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
import { expectNoAxeViolations } from '../../../../testing/axe';
import { polyfillDialog } from '../../../../testing/design-system';
import type { CurrentSession, GuardianLink, Patient } from '../../../core/api/api-types';

/** Legacy global classes replaced by the design system (MEDK-43). */
const LEGACY_CLASSES = ['button', 'alert', 'card', 'card-list'];

describe('PatientDetailPage', () => {
  let app: AppHarness;

  beforeAll(() => polyfillDialog());

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(
    patient: Patient = MINOR_PATIENT,
    links: GuardianLink[] = [CURRENT_GUARDIAN_LINK],
    account: CurrentSession = RECEPTION_ACCOUNT,
  ) {
    await signIn(app, account);
    await app.harness.navigateByUrl(`/patients/${patient.id}`);
    app.backend.expectOne(`/api/v1/patients/${patient.id}`).flush(patient);
    await app.settle();
    app.backend.expectOne(`/api/v1/patients/${patient.id}/guardian-links`).flush(links);
    await app.settle();
  }

  function withPermissions(...permissions: string[]): CurrentSession {
    return { ...ACCOUNT, roles: ['Prueba'], permissions } as CurrentSession;
  }

  /** Buttons and links filled with the accent (R-02: at most one per view). */
  function primaryActions(): string[] {
    return Array.from(app.page().querySelectorAll('.kd-button--primary'))
      .filter((element) => !element.closest('dialog'))
      .map((element) => element.textContent?.trim() ?? '');
  }

  it('shows the data, the consent and the current and historical links', async () => {
    const pastLink = { ...ENDED_AT_ADULTHOOD_LINK, endReason: 'MANUAL' as const };
    await open(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK, pastLink]);

    expect(app.page().querySelector('h1')?.textContent).toContain('John Roe');
    expect(byTestId(app.page(), 'minor-badge')).not.toBeNull();
    expect(byTestId(app.page(), 'consent-summary')?.textContent).toContain(
      'Otorgado por el representante',
    );
    expect(allByTestId(app.page(), 'current-link')[0].textContent).toContain('Mary Roe');
    expect(allByTestId(app.page(), 'current-link')[0].textContent).toContain('Representante legal');
    const past = allByTestId(app.page(), 'past-link');
    expect(past).toHaveLength(1);
    expect(past[0].textContent).toContain('Terminado por la clínica');
    expect(byTestId(app.page(), 'account')?.textContent).toContain('no tienen cuenta propia');
    expect(byTestId(app.page(), 'invite')).toBeNull();
  });

  it('shows the same personal data fields as before the redesign', async () => {
    await open(ADULT_PATIENT, []);
    const terms = Array.from(byTestId(app.page(), 'personal-data')!.querySelectorAll('dt'), (dt) =>
      dt.textContent?.trim(),
    );
    expect(terms).toEqual([
      'Documento',
      'Fecha de nacimiento',
      'Sexo',
      'Correo',
      'Teléfono',
      'Dirección',
      'Origen',
      'Registrado',
    ]);
  });

  it('builds the page only with design system components', async () => {
    await open(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK, ENDED_AT_ADULTHOOD_LINK]);
    for (const name of LEGACY_CLASSES) {
      expect(app.page().querySelectorAll(`.${name}`), `.${name}`).toHaveLength(0);
    }
    expect(app.page().querySelectorAll('kd-card').length).toBeGreaterThanOrEqual(4);
    expect(app.page().querySelector('kd-list[label="Vínculos vigentes"]')).not.toBeNull();
  });

  it('shows a loading state while the patient loads', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl(`/patients/${ADULT_PATIENT.id}`);
    await app.settle();
    expect(app.page().querySelector('kd-loading-state')?.textContent).toContain(
      'Cargando la ficha del paciente…',
    );
    app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}`).flush(ADULT_PATIENT);
    await app.settle();
    app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}/guardian-links`).flush([]);
    await app.settle();
    expect(app.page().querySelector('kd-loading-state')).toBeNull();
  });

  it('shows an error state with retry when the patient cannot be loaded', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl(`/patients/${ADULT_PATIENT.id}`);
    const { body, init } = problem(503, 'SERVICE_UNAVAILABLE');
    app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}`).flush(body, init);
    await app.settle();

    const error = byTestId(app.page(), 'load-error')!;
    expect(error.getAttribute('role')).toBe('alert');
    buttonByText(error, 'Volver a intentar').click();
    app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}`).flush(ADULT_PATIENT);
    await app.settle();
    app.backend.expectOne(`/api/v1/patients/${ADULT_PATIENT.id}/guardian-links`).flush([]);
    await app.settle();
    expect(app.page().querySelector('h1')?.textContent).toContain('Alex Poe');
  });

  it('shows an empty state when the patient has no guardian links', async () => {
    await open(ADULT_PATIENT, []);
    expect(byTestId(app.page(), 'links-empty')?.textContent).toContain(
      'El paciente no tiene representantes ni contactos registrados.',
    );
  });

  it('adds a legal guardian to a minor with an Idempotency-Key', async () => {
    await open();
    (byTestId(app.page(), 'add-guardian') as HTMLButtonElement).click();
    await app.settle();
    expect(document.activeElement?.id).toBe('link-relationship');

    const links = byTestId(app.page(), 'guardian-links') as HTMLElement;
    type(links, '#link-relationship', 'Padre');
    type(links, '#link-documentNumber', '3333333333');
    type(links, '#link-firstNames', 'Paul');
    type(links, '#link-lastNames', 'Roe');
    submit(links);

    const request = app.backend.expectOne({
      method: 'POST',
      url: `/api/v1/patients/${MINOR_PATIENT.id}/guardian-links`,
    });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.body).toEqual({
      id: expect.stringMatching(/^[0-9a-f-]{36}$/),
      relationship: 'Padre',
      person: {
        documentType: 'NATIONAL_ID',
        documentNumber: '3333333333',
        firstNames: 'Paul',
        lastNames: 'Roe',
        email: undefined,
        phone: undefined,
      },
    });
    const added: GuardianLink = {
      ...CURRENT_GUARDIAN_LINK,
      id: 'new-link',
      guardian: { id: 'g2', firstNames: 'Paul', lastNames: 'Roe' },
    };
    request.flush(added, { status: 201, statusText: 'Created' });
    app.backend
      .expectOne(`/api/v1/patients/${MINOR_PATIENT.id}/guardian-links`)
      .flush([added, CURRENT_GUARDIAN_LINK]);
    await app.settle();

    expect(byTestId(app.page(), 'links-notice')?.textContent).toContain(
      'Se agregó a Paul Roe como representante legal.',
    );
    expect(allByTestId(app.page(), 'current-link')).toHaveLength(2);
  });

  it('shows the relationship error once the form is submitted empty', async () => {
    await open();
    (byTestId(app.page(), 'add-guardian') as HTMLButtonElement).click();
    await app.settle();
    submit(byTestId(app.page(), 'guardian-links') as HTMLElement);
    await app.settle();

    const relationship = app.page().querySelector('#link-relationship')!;
    expect(relationship.getAttribute('aria-invalid')).toBe('true');
    expect(app.page().textContent).toContain('Este campo es obligatorio.');
  });

  it('asks for confirmation in a dialog before ending a link', async () => {
    await open();
    buttonByText(app.page(), 'Terminar vínculo').click();
    await app.settle();

    const dialog = app.page().querySelector('dialog')!;
    expect(dialog.hasAttribute('open')).toBe(true);
    expect(dialog.textContent).toContain('¿Terminar el vínculo con Mary Roe?');
    expect(dialog.querySelector('.kd-button--danger')?.textContent).toContain(
      'Sí, terminar el vínculo',
    );
    expect(document.activeElement).toBe(byTestId(app.page(), 'confirm-end'));

    buttonByText(dialog, 'Cancelar').click();
    await app.settle();
    expect(dialog.hasAttribute('open')).toBe(false);
    expect(document.activeElement?.getAttribute('data-end-link')).toBe(CURRENT_GUARDIAN_LINK.id);
  });

  it('ends a link after confirmation and explains the last legal guardian rule', async () => {
    await open();
    buttonByText(app.page(), 'Terminar vínculo').click();
    await app.settle();
    (byTestId(app.page(), 'confirm-end') as HTMLButtonElement).click();

    const { body, init } = problem(409, 'LAST_LEGAL_GUARDIAN');
    app.backend
      .expectOne({
        method: 'POST',
        url: `/api/v1/patients/${MINOR_PATIENT.id}/guardian-links/${CURRENT_GUARDIAN_LINK.id}/end`,
      })
      .flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'links-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.LAST_LEGAL_GUARDIAN,
    );
    expect(app.page().querySelector('dialog')?.hasAttribute('open')).toBe(false);
  });

  it('ends a link and reloads the history', async () => {
    await open();
    buttonByText(app.page(), 'Terminar vínculo').click();
    await app.settle();
    (byTestId(app.page(), 'confirm-end') as HTMLButtonElement).click();
    const ended = {
      ...CURRENT_GUARDIAN_LINK,
      current: false,
      validTo: '2031-01-01T00:00:00Z',
      endReason: 'MANUAL' as const,
    };
    app.backend
      .expectOne(
        `/api/v1/patients/${MINOR_PATIENT.id}/guardian-links/${CURRENT_GUARDIAN_LINK.id}/end`,
      )
      .flush(ended);
    app.backend.expectOne(`/api/v1/patients/${MINOR_PATIENT.id}/guardian-links`).flush([ended]);
    await app.settle();

    expect(byTestId(app.page(), 'links-notice')?.textContent).toContain(
      'Se terminó el vínculo con Mary Roe.',
    );
    expect(byTestId(app.page(), 'no-current')).not.toBeNull();
    expect(allByTestId(app.page(), 'past-link')).toHaveLength(1);
  });

  it('invites an adult patient to activate the account', async () => {
    await open(ADULT_PATIENT, [ENDED_AT_ADULTHOOD_LINK]);
    expect(byTestId(app.page(), 'adult-hint')?.textContent).toContain('solo él puede autorizar');
    expect(byTestId(app.page(), 'add-guardian')).toBeNull();

    (byTestId(app.page(), 'invite') as HTMLButtonElement).click();
    const request = app.backend.expectOne({
      method: 'POST',
      url: `/api/v1/patients/${ADULT_PATIENT.id}/account-invitation`,
    });
    expect(request.request.body).toEqual({ channel: 'EMAIL' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent).toContain('correo registrado');
    expect(byTestId(app.page(), 'account-status')?.textContent?.trim()).toBe('Invitación enviada');
  });

  it('explains when the invitation channel is unavailable', async () => {
    await open(ADULT_PATIENT, []);
    (byTestId(app.page(), 'invite') as HTMLButtonElement).click();
    const { body, init } = problem(409, 'CONTACT_CHANNEL_UNAVAILABLE');
    app.backend
      .expectOne(`/api/v1/patients/${ADULT_PATIENT.id}/account-invitation`)
      .flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'invite-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.CONTACT_CHANNEL_UNAVAILABLE,
    );
  });

  it('records the current consent when it is missing, with an Idempotency-Key', async () => {
    await open({ ...ADULT_PATIENT, currentConsent: null }, []);
    expect(byTestId(app.page(), 'consent-summary')?.textContent).toContain(
      'No hay un consentimiento vigente.',
    );
    buttonByText(app.page(), 'Registrar consentimiento').click();
    const request = app.backend.expectOne({
      method: 'POST',
      url: `/api/v1/patients/${ADULT_PATIENT.id}/consents`,
    });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.body).toEqual({
      id: expect.any(String),
      textVersion: '2026-01',
      grantedBy: 'PATIENT',
    });
    request.flush(ADULT_PATIENT.currentConsent ?? null, { status: 201, statusText: 'Created' });
    await app.settle();

    expect(byTestId(app.page(), 'consent-summary')?.textContent).toContain('versión 2026-01');
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se registró el consentimiento');
  });

  describe('one primary action per view (R-02)', () => {
    it('is the consent when it is missing', async () => {
      await open({ ...ADULT_PATIENT, currentConsent: null }, []);
      expect(primaryActions()).toEqual(['Registrar consentimiento (versión 2026-01)']);
    });

    it('is the invitation when the consent is current', async () => {
      await open(ADULT_PATIENT, []);
      expect(primaryActions()).toEqual(['Enviar invitación']);
    });

    it('is none when nothing is pending', async () => {
      await open(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK]);
      expect(primaryActions()).toEqual([]);
    });

    it('is none while the add guardian form is closed, and its submit once open', async () => {
      await open(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK]);
      (byTestId(app.page(), 'add-guardian') as HTMLButtonElement).click();
      await app.settle();
      expect(primaryActions()).toEqual(['Agregar representante']);
    });
  });

  describe('visibility by permission', () => {
    it('only shows read actions without the management permissions', async () => {
      await open(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK], PATIENTS_READER_ACCOUNT);
      expect(byTestId(app.page(), 'edit-link')).toBeNull();
      expect(byTestId(app.page(), 'add-guardian')).toBeNull();
      expect(app.page().textContent).not.toContain('Terminar vínculo');
    });

    it('hides consent and invitation without patients.register', async () => {
      await open(
        { ...ADULT_PATIENT, currentConsent: null },
        [],
        withPermissions('patients.read', 'patients.edit', 'patients.guardians.manage'),
      );
      expect(byTestId(app.page(), 'edit-link')).not.toBeNull();
      expect(app.page().textContent).not.toContain('Registrar consentimiento');
      expect(byTestId(app.page(), 'invite')).toBeNull();
    });

    it('shows consent and invitation with patients.register, without edit or links', async () => {
      await open(
        { ...ADULT_PATIENT, currentConsent: null },
        [],
        withPermissions('patients.read', 'patients.register'),
      );
      expect(byTestId(app.page(), 'edit-link')).toBeNull();
      expect(app.page().textContent).toContain('Registrar consentimiento');
      expect(byTestId(app.page(), 'invite')).not.toBeNull();
      expect(byTestId(app.page(), 'adult-hint')).toBeNull();
    });

    it('manages guardian links only with patients.guardians.manage', async () => {
      await open(
        MINOR_PATIENT,
        [CURRENT_GUARDIAN_LINK],
        withPermissions('patients.read', 'patients.guardians.manage'),
      );
      expect(byTestId(app.page(), 'edit-link')).toBeNull();
      expect(byTestId(app.page(), 'add-guardian')).not.toBeNull();
      expect(app.page().textContent).toContain('Terminar vínculo');
    });
  });

  describe('accessibility (axe)', () => {
    it('minor patient with links and the management actions', async () => {
      await open(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK, ENDED_AT_ADULTHOOD_LINK]);
      await expectNoAxeViolations(app.page());
    });

    it('adult patient with consent and invitation pending', async () => {
      await open({ ...ADULT_PATIENT, currentConsent: null }, []);
      await expectNoAxeViolations(app.page());
    });

    it('add guardian form and end link dialog', async () => {
      await open();
      (byTestId(app.page(), 'add-guardian') as HTMLButtonElement).click();
      await app.settle();
      buttonByText(app.page(), 'Terminar vínculo').click();
      await app.settle();
      await expectNoAxeViolations(app.page());
    });

    it('error state', async () => {
      await signIn(app, RECEPTION_ACCOUNT);
      await app.harness.navigateByUrl('/patients/unknown');
      const { body, init } = problem(404, 'NOT_FOUND');
      app.backend.expectOne('/api/v1/patients/unknown').flush(body, init);
      await app.settle();
      await expectNoAxeViolations(app.page());
    });
  });

  it('says so when the patient does not exist in the clinic', async () => {
    await signIn(app, RECEPTION_ACCOUNT);
    await app.harness.navigateByUrl('/patients/unknown');
    const { body, init } = problem(404, 'NOT_FOUND');
    app.backend.expectOne('/api/v1/patients/unknown').flush(body, init);
    await app.settle();
    expect(byTestId(app.page(), 'load-error')?.textContent).toContain(ERROR_MESSAGES.NOT_FOUND);
    expect(app.page().textContent).not.toContain('Volver a intentar');
  });
});
