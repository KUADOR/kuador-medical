// Simulated API for the visual tests: every browser request to /api/ is answered here with
// synthetic data, so screenshots never depend on a running API or on real (tenant) data.
// A request without a declared response is answered with 404 and recorded: the test fails.
import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';
import type {
  AuthenticatedResult,
  ClinicSelectionResult,
  CurrentSession,
  Patient,
  PatientPage,
  SystemInfo,
} from '../../../src/app/core/api/api-types';

export interface SimulatedResponse {
  status?: number;
  body: unknown;
}

/** `METHOD /path` (path after /api/v1) → response. */
export type SimulatedRoutes = Record<string, SimulatedResponse>;

const CLINIC = { id: '00000000-0000-4000-8000-0000000000a1', name: 'Clínica Ficticia' };
const BRANCH = { id: '00000000-0000-4000-8000-0000000000b1', name: 'Sucursal Centro' };

function syntheticPatient(n: number, overrides: Partial<Patient> = {}): Patient {
  return {
    id: `00000000-0000-4000-8000-0000000000c${n}`,
    documentType: 'NATIONAL_ID',
    documentNumber: `000000000${n}`,
    firstNames: 'Paciente',
    lastNames: `Ficticio ${['Uno', 'Dos', 'Tres', 'Cuatro'][n - 1] ?? n}`,
    birthDate: '1990-01-0' + n,
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
    ...overrides,
  };
}

/** Clinic staff with the patient search and the administration pages. */
const staffAccount: CurrentSession = {
  accountId: '00000000-0000-4000-8000-000000000001',
  kind: 'STAFF',
  displayName: 'Persona Ficticia',
  scope: 'TENANT',
  clinic: CLINIC,
  activeBranch: BRANCH,
  availableBranches: [BRANCH],
  roles: ['ADMIN'],
  permissions: ['patients.read', 'patients.register', 'identity.users.read', 'roles.read'],
  enabledModules: ['identity', 'patients'],
  mustChangePassword: false,
};

/** Synthetic data: fictitious values only, never copied from a real clinic. */
export const SYNTHETIC = {
  login: {
    status: 'AUTHENTICATED',
    accessToken: 'synthetic-access',
    tokenType: 'Bearer',
    expiresIn: 900,
    refreshToken: 'synthetic-refresh',
    refreshExpiresIn: 604800,
    scope: 'TENANT',
    clinic: CLINIC,
    branch: BRANCH,
    mustChangePassword: false,
  } satisfies AuthenticatedResult,
  clinicSelection: {
    status: 'CLINIC_SELECTION_REQUIRED',
    preAuthToken: 'synthetic-pre-auth',
    preAuthExpiresIn: 300,
    clinics: [CLINIC, { id: '00000000-0000-4000-8000-0000000000a2', name: 'Consultorio Ejemplo' }],
  } satisfies ClinicSelectionResult,
  staffAccount,
  /** Same staff without patients.read: the search must not be offered (R9). */
  staffWithoutPatients: {
    ...staffAccount,
    permissions: ['identity.users.read', 'roles.read'],
  } satisfies CurrentSession,
  /** Same staff in a plan without the patients module (R9). */
  staffPlanWithoutPatients: {
    ...staffAccount,
    enabledModules: ['identity'],
  } satisfies CurrentSession,
  patients: {
    items: [
      syntheticPatient(1),
      syntheticPatient(2, { accountStatus: 'ACTIVE' }),
      syntheticPatient(3, { minor: true, birthDate: '2016-05-03' }),
    ],
    page: 0,
    size: 20,
    totalItems: 3,
  } satisfies PatientPage,
  noPatients: { items: [], page: 0, size: 20, totalItems: 0 } satisfies PatientPage,
  patient: syntheticPatient(1),
  systemInfoUp: {
    name: 'Medical Kuador',
    version: '0.0.0-visual',
    status: 'UP',
    database: 'UP',
  } satisfies SystemInfo,
  systemInfoDegraded: {
    name: 'Medical Kuador',
    version: '0.0.0-visual',
    status: 'UP',
    database: 'DOWN',
  } satisfies SystemInfo,
};

export interface SimulatedApi {
  /** Fails the test if the page called an endpoint the test did not declare. */
  expectNoUnexpectedCalls(): void;
}

export async function simulateApi(page: Page, routes: SimulatedRoutes = {}): Promise<SimulatedApi> {
  const unexpected: string[] = [];

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method()} ${path}`;
    const response = routes[key];
    if (!response) {
      unexpected.push(key);
      await route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 404, code: 'NOT_FOUND', title: 'Not simulated' }),
      });
      return;
    }
    await route.fulfill({
      status: response.status ?? 200,
      contentType: 'application/json',
      body: JSON.stringify(response.body),
    });
  });

  return {
    expectNoUnexpectedCalls: () =>
      expect(unexpected, 'API calls without a simulated response').toEqual([]),
  };
}
