// Secondary patient screens migrated in MEDK-44 (only the look changes): public patient
// self-registration, self-registration settings and the adult patient's authorized contacts.
// Light, dark and mobile screenshots of the content, axe with color contrast, against the
// simulated API with synthetic data only.
import { expect, test } from '@playwright/test';
import type { GuardianLink, RegistrationSettings } from '../../src/app/core/api/api-types';
import { PATIENT_ACCOUNT, STAFF_OWNER, captureScreen, signIn } from './support/secondary-screens';
import { simulateApi } from './support/simulated-api';

const SETTINGS_OFF: RegistrationSettings = {
  selfRegistrationEnabled: false,
  publicCode: 'clinica-ficticia',
  version: 1,
};
const SETTINGS_ON: RegistrationSettings = { ...SETTINGS_OFF, selfRegistrationEnabled: true };

/** Former legal guardian whose link ended when the patient came of age. */
const ENDED_LINK: GuardianLink = {
  id: '00000000-0000-4000-8000-0000000000f1',
  guardian: {
    id: '00000000-0000-4000-8000-0000000000e1',
    firstNames: 'Persona',
    lastNames: 'Representante Ficticia',
  },
  linkType: 'LEGAL_GUARDIAN',
  relationship: 'Madre',
  validFrom: '2030-01-01T15:00:00Z',
  validTo: '2033-06-01T05:00:00Z',
  endReason: 'ADULTHOOD',
  current: false,
  authorizedBy: null,
};

test.describe('patient self-registration (public)', () => {
  test('form', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/clinics/clinica-ficticia/register');
    await expect(
      page.getByRole('heading', { level: 1, name: 'Registrarme como paciente' }),
    ).toBeVisible();

    await captureScreen(page, api, 'self-registration.png');
  });

  test('form with validation errors', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/clinics/clinica-ficticia/register');
    await page.getByRole('button', { name: 'Registrarme' }).click();
    await expect(
      page.getByText('Debes aceptar el tratamiento de tus datos personales para registrarte.'),
    ).toBeVisible();

    await captureScreen(page, api, 'self-registration-errors.png');
  });
});

test.describe('self-registration settings', () => {
  test('disabled', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      'GET /patient-registration-settings': { body: SETTINGS_OFF },
    });
    await page.goto('/patients/self-registration');
    await expect(page.getByRole('switch')).toHaveAttribute('aria-checked', 'false');

    await captureScreen(page, api, 'self-registration-settings.png');
  });

  test('enabled with the public link', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      'GET /patient-registration-settings': { body: SETTINGS_ON },
    });
    await page.goto('/patients/self-registration');
    await expect(page.getByLabel('Enlace público de registro')).toBeVisible();

    await captureScreen(page, api, 'self-registration-settings-enabled.png');
  });
});

test.describe('my authorized contacts', () => {
  test('history and authorization form', async ({ page }) => {
    const api = await signIn(page, PATIENT_ACCOUNT, {
      'GET /me/guardian-links': { body: [ENDED_LINK] },
    });
    await page.goto('/my-contacts');
    await expect(page.getByRole('heading', { name: 'Autorizar a una persona' })).toBeVisible();

    await captureScreen(page, api, 'my-contacts.png');
  });

  test('someone new, with validation errors', async ({ page }) => {
    const api = await signIn(page, PATIENT_ACCOUNT, { 'GET /me/guardian-links': { body: [] } });
    await page.goto('/my-contacts');
    await page.getByRole('button', { name: 'Autorizar' }).click();
    await expect(page.getByText('Elige qué autorizas.')).toBeVisible();

    await captureScreen(page, api, 'my-contacts-errors.png');
  });
});
