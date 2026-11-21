// Secondary staff screens migrated in MEDK-44 (only the look changes): staff list, invitation
// form (with its errors and the roles per branch editor), staff record, system status and the
// message pages. Screenshot of the content in light, dark and mobile, and axe with color
// contrast, against the simulated API with synthetic data only.
import { expect, test } from '@playwright/test';
import type { Role, StaffUser, StaffUserPage } from '../../src/app/core/api/api-types';
import {
  BRANCH,
  OTHER_BRANCH,
  STAFF_OWNER,
  captureScreen,
  signIn,
} from './support/secondary-screens';
import { SYNTHETIC, simulateApi } from './support/simulated-api';

const RECEPTION: Role = {
  id: '00000000-0000-4000-8000-0000000000d1',
  name: 'Recepción',
  description: 'Atención en el mostrador.',
  systemKey: null,
  systemRole: false,
  clinical: false,
  permissions: ['patients.read'],
  version: 0,
};

const DOCTOR: Role = {
  ...RECEPTION,
  id: '00000000-0000-4000-8000-0000000000d2',
  name: 'Médico',
  description: null,
  clinical: true,
};

const INVITED: StaffUser = {
  id: '00000000-0000-4000-8000-0000000000e1',
  firstNames: 'Persona',
  lastNames: 'Invitada Ficticia',
  email: 'persona.invitada@clinica-ficticia.example.test',
  jobTitle: 'Médica general',
  professionalRegistration: 'REG-FICTICIO-1',
  status: 'INVITED',
  roleAssignments: [
    { branch: BRANCH, role: { id: DOCTOR.id, name: DOCTOR.name } },
    { branch: OTHER_BRANCH, role: { id: DOCTOR.id, name: DOCTOR.name } },
  ],
  invitationExpiresAt: '2030-01-04T15:00:00Z',
  createdAt: '2030-01-01T15:00:00Z',
  version: 0,
};

const ACTIVE: StaffUser = {
  ...INVITED,
  id: '00000000-0000-4000-8000-0000000000e2',
  firstNames: 'Persona',
  lastNames: 'Activa Ficticia',
  email: 'persona.activa@clinica-ficticia.example.test',
  jobTitle: 'Recepcionista',
  professionalRegistration: null,
  status: 'ACTIVE',
  roleAssignments: [{ branch: BRANCH, role: { id: RECEPTION.id, name: RECEPTION.name } }],
  invitationExpiresAt: null,
};

const DISABLED: StaffUser = {
  ...ACTIVE,
  id: '00000000-0000-4000-8000-0000000000e3',
  lastNames: 'Deshabilitada Ficticia',
  email: 'persona.deshabilitada@clinica-ficticia.example.test',
  status: 'DISABLED',
};

const STAFF_PAGE: StaffUserPage = {
  items: [INVITED, ACTIVE, DISABLED],
  page: 0,
  size: 20,
  totalItems: 3,
};

test.describe('secondary staff screens', () => {
  test('staff list', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, { 'GET /staff-users': { body: STAFF_PAGE } });
    await page.goto('/staff');
    await expect(page.getByRole('heading', { level: 1, name: 'Personal' })).toBeVisible();
    await expect(page.getByText('Mostrando 1–3 de 3')).toBeVisible();

    await captureScreen(page, api, 'staff-list.png');
  });

  test('staff list without results', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      'GET /staff-users': { body: { items: [], page: 0, size: 20, totalItems: 0 } },
    });
    await page.goto('/staff');
    await expect(page.getByText('No hay personas que coincidan con la búsqueda.')).toBeVisible();

    await captureScreen(page, api, 'staff-list-empty.png');
  });

  test('invitation form', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, { 'GET /roles': { body: [RECEPTION, DOCTOR] } });
    await page.goto('/staff/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Invitar persona' })).toBeVisible();
    await expect(page.getByLabel('Nombres')).toBeVisible();

    await captureScreen(page, api, 'staff-invite.png');
  });

  test('invitation form with errors and a role per branch', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, { 'GET /roles': { body: [RECEPTION, DOCTOR] } });
    await page.goto('/staff/new');
    await page.getByLabel('Un rol distinto por sucursal').check();
    await page.getByRole('button', { name: 'Registrar y enviar invitación' }).click();
    await expect(page.getByText('Elige un rol para al menos una sucursal.')).toBeVisible();
    await expect(page.getByLabel(OTHER_BRANCH.name)).toBeVisible();

    await captureScreen(page, api, 'staff-invite-errors.png');
  });

  test('staff record', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      [`GET /staff-users/${INVITED.id}`]: { body: INVITED },
      'GET /roles': { body: [RECEPTION, DOCTOR] },
    });
    await page.goto(`/staff/${INVITED.id}`);
    await expect(
      page.getByRole('heading', { level: 1, name: 'Persona Invitada Ficticia' }),
    ).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: 'Acceso' })).toBeVisible();

    await captureScreen(page, api, 'staff-record.png');
  });

  test('staff record asking to confirm the disable', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      [`GET /staff-users/${ACTIVE.id}`]: { body: ACTIVE },
      'GET /roles': { body: [RECEPTION, DOCTOR] },
    });
    await page.goto(`/staff/${ACTIVE.id}`);
    await page.getByRole('button', { name: 'Deshabilitar acceso' }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();

    await captureScreen(page, api, 'staff-record-confirm-disable.png');
  });
});

test.describe('system status and message pages', () => {
  test('system status', async ({ page }) => {
    const api = await simulateApi(page, { 'GET /system/info': { body: SYNTHETIC.systemInfoUp } });
    await page.goto('/status');
    await expect(page.getByText('0.0.0-visual')).toBeVisible();

    await captureScreen(page, api, 'status.png');
  });

  test('system status with the database down', async ({ page }) => {
    const api = await simulateApi(page, {
      'GET /system/info': { body: SYNTHETIC.systemInfoDegraded },
    });
    await page.goto('/status');
    await expect(page.getByText('No disponible')).toBeVisible();

    await captureScreen(page, api, 'status-degraded.png');
  });

  test('forbidden page', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/forbidden');
    await expect(page.getByRole('heading', { level: 1, name: 'Sin permiso' })).toBeVisible();

    await captureScreen(page, api, 'forbidden.png');
  });

  test('not found page', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/no-such-page');
    await expect(
      page.getByRole('heading', { level: 1, name: 'Página no encontrada' }),
    ).toBeVisible();

    await captureScreen(page, api, 'not-found.png');
  });
});
