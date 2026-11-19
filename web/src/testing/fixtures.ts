// Test-only fixtures, shaped by the generated contract types.
import type {
  AuthenticatedResult,
  BranchSelectionResult,
  ClinicSelectionResult,
  CurrentSession,
  PermissionInfo,
  Role,
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
    scope: 'TENANT',
    description: 'View roles and the assignable permission catalog.',
  },
  { code: 'roles.create', scope: 'TENANT', description: 'Create roles.' },
  { code: 'roles.update', scope: 'TENANT', description: 'Update roles.' },
  { code: 'roles.delete', scope: 'TENANT', description: 'Delete roles.' },
];

export const FULL_CATALOG: PermissionInfo[] = [
  ...TENANT_CATALOG,
  { code: 'platform.permissions.read', scope: 'PLATFORM', description: 'View the full catalog.' },
];

export const OWNER_ROLE: Role = {
  id: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  name: 'OWNER',
  description: null,
  systemRole: true,
  permissions: ['roles.read', 'roles.create', 'roles.update', 'roles.delete'],
  version: 0,
};

export const ADMIN_ROLE: Role = {
  ...OWNER_ROLE,
  id: '7c9e6679-7425-40de-944b-e07fc1f90ae8',
  name: 'ADMIN',
};

export const RECEPTION_ROLE: Role = {
  id: '3f2a1b0c-9d8e-4f7a-8b6c-5d4e3f2a1b0c',
  name: 'Recepción',
  description: 'Personal de recepción.',
  systemRole: false,
  permissions: ['roles.read'],
  version: 3,
};

export function problem(status: number, code: string, errors?: { field: string; code: string }[]) {
  return {
    body: { type: 'about:blank', title: 'Error', status, code, ...(errors ? { errors } : {}) },
    init: { status, statusText: 'Error' },
  };
}
