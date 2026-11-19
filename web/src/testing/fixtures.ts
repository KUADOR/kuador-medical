// Test-only fixtures, shaped by the generated contract types.
import type {
  CurrentAccount,
  LoginResponse,
  PermissionInfo,
  Role,
} from '../app/core/api/api-types';

export const LOGIN: LoginResponse = {
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresIn: 1800,
  scope: 'TENANT',
  mustChangePassword: false,
};

export const ACCOUNT: CurrentAccount = {
  accountId: '00000000-0000-0000-0000-000000000001',
  displayName: 'Test Owner',
  scope: 'TENANT',
  tenant: { id: '00000000-0000-0000-0000-0000000000a1', name: 'Test Clinic' },
  roles: ['OWNER', 'ADMIN'],
  permissions: ['roles.read', 'roles.create'],
  mustChangePassword: false,
};

export const FULL_ACCOUNT: CurrentAccount = {
  ...ACCOUNT,
  permissions: ['roles.read', 'roles.create', 'roles.update', 'roles.delete'],
};

export const READ_ONLY_ACCOUNT: CurrentAccount = {
  ...ACCOUNT,
  roles: ['Reception'],
  permissions: ['roles.read'],
};

export const PLATFORM_LOGIN: LoginResponse = {
  ...LOGIN,
  scope: 'PLATFORM',
  mustChangePassword: true,
};

export const SUPER_ADMIN: CurrentAccount = {
  accountId: '00000000-0000-0000-0000-0000000000ff',
  displayName: 'Kuador',
  scope: 'PLATFORM',
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
