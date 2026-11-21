// Support of the visual tests of the secondary screens (MEDK-44): a signed-in synthetic staff,
// patient or platform session without going through the login form, and one capture helper.
// Synthetic data only: fictitious names, ids and addresses, never copied from a real clinic.
import { type Page, expect } from '@playwright/test';
import type { CurrentSession } from '../../../src/app/core/api/api-types';
import { expectNoAxeViolations } from './axe';
import { type SimulatedApi, type SimulatedRoutes, simulateApi } from './simulated-api';

export const CLINIC = { id: '00000000-0000-4000-8000-0000000000a1', name: 'Clínica Ficticia' };
export const BRANCH = { id: '00000000-0000-4000-8000-0000000000b1', name: 'Sucursal Centro' };
export const OTHER_BRANCH = {
  id: '00000000-0000-4000-8000-0000000000b2',
  name: 'Sucursal Norte',
};

/** Clinic owner with every staff, role and patient permission of the current catalog. */
export const STAFF_OWNER: CurrentSession = {
  accountId: '00000000-0000-4000-8000-000000000001',
  kind: 'STAFF',
  displayName: 'Persona Ficticia',
  scope: 'TENANT',
  clinic: CLINIC,
  activeBranch: BRANCH,
  availableBranches: [BRANCH, OTHER_BRANCH],
  roles: ['OWNER'],
  permissions: [
    'roles.read',
    'roles.create',
    'roles.update',
    'roles.delete',
    'identity.users.read',
    'identity.users.manage',
    'identity.assignments.manage',
    'patients.read',
    'patients.register',
    'patients.edit',
    'patients.guardians.manage',
    'patients.selfRegistration.configure',
  ],
  enabledModules: ['identity', 'patients'],
  mustChangePassword: false,
};

/** Patient using the web (my authorized contacts). */
export const PATIENT_ACCOUNT: CurrentSession = {
  accountId: '00000000-0000-4000-8000-000000000002',
  kind: 'PATIENT',
  displayName: 'Paciente Ficticia',
  scope: 'TENANT',
  clinic: CLINIC,
  activeBranch: null,
  availableBranches: [],
  patientId: '00000000-0000-4000-8000-0000000000c1',
  roles: [],
  permissions: [],
  enabledModules: ['identity', 'patients'],
  mustChangePassword: false,
};

/** Kuador platform account (SUPER_ADMIN). */
export const PLATFORM_ACCOUNT: CurrentSession = {
  accountId: '00000000-0000-4000-8000-000000000003',
  kind: 'STAFF',
  displayName: 'Plataforma Ficticia',
  scope: 'PLATFORM',
  activeBranch: null,
  availableBranches: [],
  roles: ['SUPER_ADMIN'],
  permissions: ['platform.permissions.read'],
  enabledModules: [],
  mustChangePassword: false,
};

/**
 * Starts the page with a stored session (synthetic tokens, far from expiring) and answers
 * GET /me with `account`. `routes` adds the endpoints of the screen.
 */
export async function signIn(
  page: Page,
  account: CurrentSession,
  routes: SimulatedRoutes = {},
): Promise<SimulatedApi> {
  await page.addInitScript(() => {
    const far = Date.now() + 24 * 60 * 60 * 1000;
    sessionStorage.setItem(
      'mk.session',
      JSON.stringify({
        accessToken: 'synthetic-access',
        expiresAt: far,
        refreshToken: 'synthetic-refresh',
        refreshExpiresAt: far,
        mustChangePassword: false,
      }),
    );
  });
  return simulateApi(page, { 'GET /me': { body: account }, ...routes });
}

/** Moves the pointer off the content so nothing is captured in its hover state. */
export async function restPointer(page: Page): Promise<void> {
  await page.mouse.move(0, 0);
}

/**
 * Screenshot of the screen content (the `main` landmark, so the app frame redesigned in
 * MEDK-42 does not move these baselines), axe with color contrast in the current theme, and no
 * API call without a simulated response.
 */
export async function captureScreen(page: Page, api: SimulatedApi, name: string): Promise<void> {
  await restPointer(page);
  // Full page clipped to `main`: the page is not scrolled, so the sticky top bar of the app
  // frame (MEDK-42) never covers the top of a tall screen.
  await page.evaluate(() => window.scrollTo(0, 0));
  const box = await page.locator('main').boundingBox();
  expect(box, 'main landmark').not.toBeNull();
  await expect(page).toHaveScreenshot(name, { fullPage: true, clip: box! });
  await expectNoAxeViolations(page);
  api.expectNoUnexpectedCalls();
}
