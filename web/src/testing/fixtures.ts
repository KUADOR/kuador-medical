// Test-only fixtures, shaped by the generated contract types.
import type { CurrentAccount, LoginResponse } from '../app/core/api/api-types';

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
