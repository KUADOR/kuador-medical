// Test-only fixtures, shaped by the generated contract types.
import type {
  AuthenticatedResult,
  BranchSelectionResult,
  ClinicSelectionResult,
  CurrentSession,
  GuardianLink,
  Patient,
  PermissionInfo,
  PlatformPermissionInfo,
  RegistrationSettings,
  Role,
  StaffUser,
} from '../app/core/api/api-types';

export const CLINIC = { id: '00000000-0000-0000-0000-0000000000a1', name: 'Test Clinic' };
export const OTHER_CLINIC = { id: '00000000-0000-0000-0000-0000000000a2', name: 'Other Dental' };
export const MAIN_BRANCH = { id: '00000000-0000-0000-0000-0000000000b1', name: 'Main branch' };
export const NORTH_BRANCH = { id: '00000000-0000-0000-0000-0000000000b2', name: 'North branch' };

export const LOGIN: AuthenticatedResult = {
  status: 'AUTHENTICATED',
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'test-refresh',
  refreshExpiresIn: 604800,
  scope: 'TENANT',
  clinic: CLINIC,
  branch: MAIN_BRANCH,
  mustChangePassword: false,
};

export const REFRESHED: AuthenticatedResult = {
  ...LOGIN,
  accessToken: 'refreshed-token',
  refreshToken: 'refreshed-refresh',
};

export const CLINIC_SELECTION: ClinicSelectionResult = {
  status: 'CLINIC_SELECTION_REQUIRED',
  preAuthToken: 'pre-auth-token',
  preAuthExpiresIn: 300,
  clinics: [CLINIC, OTHER_CLINIC],
};

export const BRANCH_SELECTION: BranchSelectionResult = {
  status: 'BRANCH_SELECTION_REQUIRED',
  preAuthToken: 'pre-auth-token-2',
  preAuthExpiresIn: 300,
  clinic: CLINIC,
  branches: [MAIN_BRANCH, NORTH_BRANCH],
};

export const ACCOUNT: CurrentSession = {
  accountId: '00000000-0000-0000-0000-000000000001',
  kind: 'STAFF',
  displayName: 'Test Owner',
  scope: 'TENANT',
  clinic: CLINIC,
  activeBranch: MAIN_BRANCH,
  availableBranches: [MAIN_BRANCH],
  roles: ['OWNER', 'ADMIN'],
  permissions: ['roles.read', 'roles.create'],
  enabledModules: ['identity', 'patients'],
  mustChangePassword: false,
};

export const MULTI_BRANCH_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  availableBranches: [MAIN_BRANCH, NORTH_BRANCH],
};

export const FULL_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  permissions: ['roles.read', 'roles.create', 'roles.update', 'roles.delete'],
};

export const READ_ONLY_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  roles: ['Reception'],
  permissions: ['roles.read'],
};

export const PLATFORM_LOGIN: AuthenticatedResult = {
  ...LOGIN,
  scope: 'PLATFORM',
  clinic: undefined,
  branch: null,
  mustChangePassword: true,
};

export const SUPER_ADMIN: CurrentSession = {
  accountId: '00000000-0000-0000-0000-0000000000ff',
  kind: 'STAFF',
  displayName: 'Kuador',
  scope: 'PLATFORM',
  activeBranch: null,
  availableBranches: [],
  enabledModules: [],
  roles: ['SUPER_ADMIN'],
  permissions: ['platform.permissions.read'],
  mustChangePassword: false,
};

export const TENANT_CATALOG: PermissionInfo[] = [
  {
    code: 'roles.read',
    module: 'identity',
    scope: 'TENANT',
    description: 'Ver roles y el catálogo de permisos.',
  },
  { code: 'roles.create', module: 'identity', scope: 'TENANT', description: 'Crear roles.' },
  { code: 'roles.update', module: 'identity', scope: 'TENANT', description: 'Editar roles.' },
  { code: 'roles.delete', module: 'identity', scope: 'TENANT', description: 'Eliminar roles.' },
];

export const PATIENTS_CATALOG: PermissionInfo[] = [
  { code: 'patients.read', module: 'patients', scope: 'TENANT', description: 'Ver pacientes.' },
  {
    code: 'patients.register',
    module: 'patients',
    scope: 'TENANT',
    description: 'Registrar pacientes.',
  },
];

export const FULL_CATALOG: PlatformPermissionInfo[] = [
  ...TENANT_CATALOG,
  { code: 'platform.permissions.read', scope: 'PLATFORM', description: 'View the full catalog.' },
];

export const OWNER_ROLE: Role = {
  id: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  name: 'OWNER',
  description: null,
  systemKey: 'OWNER',
  systemRole: true,
  clinical: false,
  permissions: ['roles.read', 'roles.create', 'roles.update', 'roles.delete'],
  version: 0,
};

export const ADMIN_ROLE: Role = {
  ...OWNER_ROLE,
  id: '7c9e6679-7425-40de-944b-e07fc1f90ae8',
  name: 'ADMIN',
  systemKey: 'ADMIN',
};

export const RECEPTION_ROLE: Role = {
  id: '3f2a1b0c-9d8e-4f7a-8b6c-5d4e3f2a1b0c',
  name: 'Recepción',
  description: 'Personal de recepción.',
  systemKey: null,
  systemRole: false,
  clinical: false,
  permissions: ['roles.read'],
  version: 3,
};

export const DOCTOR_ROLE: Role = {
  id: '4b3a2c1d-0e9f-4a8b-8c7d-6e5f4a3b2c1d',
  name: 'Médico',
  description: null,
  systemKey: null,
  systemRole: false,
  clinical: true,
  permissions: ['patients.read'],
  version: 1,
};

/** Clinic manager with every staff permission, but not OWNER. */
export const STAFF_ADMIN_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  roles: ['ADMIN'],
  availableBranches: [MAIN_BRANCH, NORTH_BRANCH],
  permissions: [
    'roles.read',
    'roles.create',
    'roles.update',
    'roles.delete',
    'identity.users.read',
    'identity.users.manage',
    'identity.assignments.manage',
    'patients.read',
  ],
};

export const STAFF_OWNER_ACCOUNT: CurrentSession = {
  ...STAFF_ADMIN_ACCOUNT,
  roles: ['OWNER'],
};

/** Can see the staff list and records, nothing else. */
export const STAFF_READER_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  roles: ['Recepción'],
  permissions: ['identity.users.read'],
};

export const INVITED_DOCTOR: StaffUser = {
  id: '1d2c3b4a-5f6e-4789-8a9b-0c1d2e3f4a5b',
  firstNames: 'Ana',
  lastNames: 'Pérez',
  email: 'ana.perez@example.com',
  jobTitle: 'Médica general',
  professionalRegistration: 'REG-0000-EXAMPLE',
  status: 'INVITED',
  roleAssignments: [
    { branch: MAIN_BRANCH, role: { id: DOCTOR_ROLE.id, name: DOCTOR_ROLE.name } },
    { branch: NORTH_BRANCH, role: { id: DOCTOR_ROLE.id, name: DOCTOR_ROLE.name } },
  ],
  invitationExpiresAt: '2030-01-04T15:00:00Z',
  createdAt: '2030-01-01T15:00:00Z',
  version: 2,
};

export const ACTIVE_RECEPTIONIST: StaffUser = {
  id: '2e3d4c5b-6a7f-4890-9bac-1d2e3f4a5b6c',
  firstNames: 'Luis',
  lastNames: 'Gómez',
  email: 'luis.gomez@example.com',
  jobTitle: 'Recepcionista',
  professionalRegistration: null,
  status: 'ACTIVE',
  roleAssignments: [
    { branch: MAIN_BRANCH, role: { id: RECEPTION_ROLE.id, name: RECEPTION_ROLE.name } },
  ],
  invitationExpiresAt: null,
  createdAt: '2030-01-01T15:00:00Z',
  version: 5,
};

export function problem(status: number, code: string, errors?: { field: string; code: string }[]) {
  return {
    body: { type: 'about:blank', title: 'Error', status, code, ...(errors ? { errors } : {}) },
    init: { status, statusText: 'Error' },
  };
}

// ---------------------------------------------------------------- patients (MEDK-22)

export const RECEPTION_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  roles: ['Recepción'],
  permissions: ['patients.read', 'patients.register', 'patients.edit', 'patients.guardians.manage'],
};

export const PATIENTS_ADMIN_ACCOUNT: CurrentSession = {
  ...RECEPTION_ACCOUNT,
  roles: ['ADMIN'],
  permissions: [...RECEPTION_ACCOUNT.permissions, 'patients.selfRegistration.configure'],
};

export const PATIENTS_READER_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  roles: ['Consulta'],
  permissions: ['patients.read'],
};

export const PATIENT_ACCOUNT: CurrentSession = {
  ...ACCOUNT,
  accountId: '00000000-0000-0000-0000-000000000010',
  kind: 'PATIENT',
  displayName: 'Alex Poe',
  activeBranch: null,
  availableBranches: [],
  patientId: '00000000-0000-0000-0000-0000000000c1',
  roles: [],
  permissions: [],
};

export const ADULT_PATIENT: Patient = {
  id: '00000000-0000-0000-0000-0000000000c1',
  documentType: 'NATIONAL_ID',
  documentNumber: '2222222222',
  firstNames: 'Alex',
  lastNames: 'Poe',
  birthDate: '1990-03-15',
  sex: 'FEMALE',
  email: 'alex.poe@example.com',
  phone: null,
  address: null,
  minor: false,
  adultSince: null,
  origin: 'STAFF',
  accountStatus: 'NONE',
  currentConsent: {
    id: '00000000-0000-0000-0000-0000000000d1',
    textVersion: '2026-01',
    grantedAt: '2030-01-01T15:00:00Z',
    grantedBy: 'PATIENT',
    channel: 'WEB',
    revokedAt: null,
  },
  createdAt: '2030-01-01T15:00:00Z',
  version: 2,
};

export const MINOR_PATIENT: Patient = {
  ...ADULT_PATIENT,
  id: '00000000-0000-0000-0000-0000000000c2',
  documentNumber: '0000000000',
  firstNames: 'John',
  lastNames: 'Roe',
  birthDate: '2015-06-01',
  sex: 'MALE',
  email: null,
  minor: true,
  currentConsent: { ...ADULT_PATIENT.currentConsent!, grantedBy: 'GUARDIAN' },
  version: 0,
};

export const GUARDIAN = {
  id: '00000000-0000-0000-0000-0000000000e1',
  firstNames: 'Mary',
  lastNames: 'Roe',
};

export const CURRENT_GUARDIAN_LINK: GuardianLink = {
  id: '00000000-0000-0000-0000-0000000000f1',
  guardian: GUARDIAN,
  linkType: 'LEGAL_GUARDIAN',
  relationship: 'Madre',
  validFrom: '2030-01-01T15:00:00Z',
  validTo: null,
  endReason: null,
  current: true,
  authorizedBy: null,
};

export const ENDED_AT_ADULTHOOD_LINK: GuardianLink = {
  ...CURRENT_GUARDIAN_LINK,
  id: '00000000-0000-0000-0000-0000000000f2',
  validTo: '2033-06-01T05:00:00Z',
  endReason: 'ADULTHOOD',
  current: false,
};

export const REGISTRATION_SETTINGS: RegistrationSettings = {
  selfRegistrationEnabled: false,
  publicCode: 'test-clinic',
  version: 4,
};
