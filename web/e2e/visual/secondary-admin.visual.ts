// Secondary administration screens migrated in MEDK-44: roles list, role form, permission
// catalog (SUPER_ADMIN) and the patient registration and edit form. Only the look changes:
// the same steps and calls, against the simulated API with synthetic data only.
import { expect, test } from '@playwright/test';
import type {
  Patient,
  PermissionInfo,
  PlatformPermissionInfo,
  Role,
} from '../../src/app/core/api/api-types';
import {
  PLATFORM_ACCOUNT,
  STAFF_OWNER,
  captureScreen,
  signIn,
} from './support/secondary-screens';

const CATALOG: PermissionInfo[] = [
  { code: 'roles.read', module: 'identity', scope: 'TENANT', description: 'Ver roles.' },
  { code: 'roles.create', module: 'identity', scope: 'TENANT', description: 'Crear roles.' },
  { code: 'roles.update', module: 'identity', scope: 'TENANT', description: 'Editar roles.' },
  { code: 'roles.delete', module: 'identity', scope: 'TENANT', description: 'Eliminar roles.' },
  { code: 'patients.read', module: 'patients', scope: 'TENANT', description: 'Ver pacientes.' },
  {
    code: 'patients.register',
    module: 'patients',
    scope: 'TENANT',
    description: 'Registrar pacientes.',
  },
];

const PLATFORM_CATALOG: PlatformPermissionInfo[] = [
  ...CATALOG.map(({ code, scope, description }) => ({ code, scope, description })),
  {
    code: 'platform.permissions.read',
    scope: 'PLATFORM',
    description: 'Ver el catálogo completo.',
  },
];

const OWNER_ROLE: Role = {
  id: '00000000-0000-4000-8000-0000000000e1',
  name: 'OWNER',
  description: null,
  systemKey: 'OWNER',
  systemRole: true,
  clinical: false,
  permissions: CATALOG.map((p) => p.code),
  version: 0,
};

const RECEPTION_ROLE: Role = {
  id: '00000000-0000-4000-8000-0000000000e2',
  name: 'Recepción ficticia',
  description: 'Rol de prueba para la recepción.',
  systemKey: null,
  systemRole: false,
  clinical: false,
  permissions: ['roles.read', 'patients.read'],
  version: 1,
};

const DOCTOR_ROLE: Role = {
  id: '00000000-0000-4000-8000-0000000000e3',
  name: 'Medicina ficticia',
  description: null,
  systemKey: null,
  systemRole: false,
  clinical: true,
  permissions: ['patients.read'],
  version: 1,
};

const PATIENT: Patient = {
  id: '00000000-0000-4000-8000-0000000000c1',
  documentType: 'NATIONAL_ID',
  documentNumber: '0000000001',
  firstNames: 'Paciente',
  lastNames: 'Ficticio Uno',
  birthDate: '1990-01-01',
  sex: 'FEMALE',
  email: null,
  phone: null,
  address: null,
  minor: false,
  adultSince: null,
  origin: 'STAFF',
  accountStatus: 'NONE',
  currentConsent: null,
  createdAt: '2030-01-01T15:00:00Z',
  version: 0,
};

test.describe('roles', () => {
  test('roles list', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      'GET /roles': { body: [OWNER_ROLE, RECEPTION_ROLE, DOCTOR_ROLE] },
    });
    await page.goto('/roles');
    await expect(page.getByRole('heading', { level: 1, name: 'Roles' })).toBeVisible();

    await captureScreen(page, api, 'roles.png');
  });

  test('roles list asking to confirm a delete', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      'GET /roles': { body: [OWNER_ROLE, RECEPTION_ROLE] },
    });
    await page.goto('/roles');
    await page.getByRole('button', { name: 'Eliminar Recepción ficticia' }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();

    await captureScreen(page, api, 'roles-confirm-delete.png');
  });

  test('new role with validation errors', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, { 'GET /permissions': { body: CATALOG } });
    await page.goto('/roles/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Nuevo rol' })).toBeVisible();
    await page.getByRole('button', { name: 'Crear rol' }).click();
    await expect(page.getByText('Este campo es obligatorio.').first()).toBeVisible();

    await captureScreen(page, api, 'role-new-errors.png');
  });

  test('protected role, read-only', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      'GET /permissions': { body: CATALOG },
      [`GET /roles/${OWNER_ROLE.id}`]: { body: OWNER_ROLE },
    });
    await page.goto(`/roles/${OWNER_ROLE.id}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Rol OWNER' })).toBeVisible();

    await captureScreen(page, api, 'role-read-only.png');
  });
});

test.describe('platform', () => {
  test('permission catalog', async ({ page }) => {
    const api = await signIn(page, PLATFORM_ACCOUNT, {
      'GET /platform/permissions': { body: PLATFORM_CATALOG },
    });
    await page.goto('/platform/permissions');
    await expect(page.getByRole('heading', { level: 1, name: 'Catálogo de permisos' })).toBeVisible();

    await captureScreen(page, api, 'platform-permissions.png');
  });
});

test.describe('patient form', () => {
  test('new patient', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER);
    await page.goto('/patients/new');
    await expect(page.getByLabel('Número de documento')).toBeVisible();

    await captureScreen(page, api, 'patient-new.png');
  });

  test('new minor patient with the guardian section and validation errors', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER);
    await page.goto('/patients/new');
    await page.getByLabel('Fecha de nacimiento').fill('2016-05-03');
    await expect(page.getByTestId('guardian-section')).toBeVisible();
    await page.getByRole('button', { name: 'Registrar paciente' }).click();
    await expect(page.getByText('Este campo es obligatorio.').first()).toBeVisible();

    await captureScreen(page, api, 'patient-new-minor-errors.png');
  });

  test('edit patient', async ({ page }) => {
    const api = await signIn(page, STAFF_OWNER, {
      [`GET /patients/${PATIENT.id}`]: { body: PATIENT },
    });
    await page.goto(`/patients/${PATIENT.id}/edit`);
    await expect(page.getByLabel('Nombres')).toHaveValue('Paciente');

    await captureScreen(page, api, 'patient-edit.png');
  });
});
