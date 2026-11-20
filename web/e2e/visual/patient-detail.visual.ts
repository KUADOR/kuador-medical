// Patient record (MEDK-43): screenshots in light, dark and mobile, axe with color contrast in a
// real browser, and the number of actions of each task of the record. Synthetic data only
// (the fictitious fixtures of the unit tests): never data of a real clinic.
import { type Page, expect, test } from '@playwright/test';
import type { CurrentSession, GuardianLink, Patient } from '../../src/app/core/api/api-types';
import {
  ADULT_PATIENT,
  CURRENT_GUARDIAN_LINK,
  ENDED_AT_ADULTHOOD_LINK,
  MINOR_PATIENT,
  PATIENTS_READER_ACCOUNT,
  RECEPTION_ACCOUNT,
} from '../../src/testing/fixtures';
import { expectNoAxeViolations } from './support/axe';
import { type SimulatedRoutes, simulateApi } from './support/simulated-api';

/** A signed-in session in sessionStorage, as the app stores it after the login. */
async function signIn(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const hour = 60 * 60 * 1000;
    sessionStorage.setItem(
      'mk.session',
      JSON.stringify({
        accessToken: 'synthetic-access',
        expiresAt: Date.now() + hour,
        refreshToken: 'synthetic-refresh',
        refreshExpiresAt: Date.now() + 8 * hour,
        mustChangePassword: false,
      }),
    );
  });
}

function routesFor(
  patient: Patient,
  links: GuardianLink[],
  account: CurrentSession = RECEPTION_ACCOUNT,
): SimulatedRoutes {
  return {
    'GET /me': { body: account },
    [`GET /patients/${patient.id}`]: { body: patient },
    [`GET /patients/${patient.id}/guardian-links`]: { body: links },
  };
}

async function openRecord(page: Page, patient: Patient, routes: SimulatedRoutes) {
  await signIn(page);
  const api = await simulateApi(page, routes);
  await page.goto(`/patients/${patient.id}`);
  return api;
}

async function expectRecordShown(page: Page, patient: Patient): Promise<void> {
  const name = `${patient.firstNames} ${patient.lastNames}`;
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
  await expect(page.getByTestId('guardian-links')).toBeVisible();
  await expect(page.locator('kd-loading-state')).toHaveCount(0);
}

const PAST_LINK: GuardianLink = { ...ENDED_AT_ADULTHOOD_LINK, endReason: 'MANUAL' };
const ADULT_PENDING: Patient = { ...ADULT_PATIENT, currentConsent: null, phone: '0990000000' };

test.describe('patient record', () => {
  test('minor patient with current and past links', async ({ page }) => {
    const api = await openRecord(
      page,
      MINOR_PATIENT,
      routesFor(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK, PAST_LINK]),
    );
    await expectRecordShown(page, MINOR_PATIENT);

    await expect(page).toHaveScreenshot('record-minor.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('adult patient with consent and invitation pending', async ({ page }) => {
    const api = await openRecord(page, ADULT_PENDING, routesFor(ADULT_PENDING, []));
    await expectRecordShown(page, ADULT_PENDING);
    await expect(page.getByTestId('invite')).toBeVisible();

    await expect(page).toHaveScreenshot('record-adult-pending.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('read only, without the management permissions', async ({ page }) => {
    const api = await openRecord(
      page,
      ADULT_PENDING,
      routesFor(ADULT_PENDING, [], PATIENTS_READER_ACCOUNT),
    );
    await expectRecordShown(page, ADULT_PENDING);
    await expect(page.getByTestId('edit-link')).toHaveCount(0);
    await expect(page.getByTestId('invite')).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Registrar consentimiento/ })).toHaveCount(0);

    await expect(page).toHaveScreenshot('record-read-only.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('end link dialog', async ({ page }) => {
    const api = await openRecord(
      page,
      MINOR_PATIENT,
      routesFor(MINOR_PATIENT, [CURRENT_GUARDIAN_LINK]),
    );
    await expectRecordShown(page, MINOR_PATIENT);
    await page.locator(`[data-end-link="${CURRENT_GUARDIAN_LINK.id}"]`).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    await expect(page).toHaveScreenshot('record-end-link-dialog.png');
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('load error with retry', async ({ page }) => {
    const api = await openRecord(page, MINOR_PATIENT, {
      'GET /me': { body: RECEPTION_ACCOUNT },
      [`GET /patients/${MINOR_PATIENT.id}`]: {
        status: 500,
        body: { status: 500, code: 'INTERNAL', title: 'Synthetic failure' },
      },
    });
    await expect(page.getByTestId('load-error')).toBeVisible();

    await expect(page).toHaveScreenshot('record-load-error.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });
});
