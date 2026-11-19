// Friendly aliases over the types generated from contracts/identity.openapi.yaml.
// Regenerate with `npm run api:generate`; never edit identity.schema.d.ts by hand.
import type { components, paths } from './identity.schema';

type Schemas = components['schemas'];

export type SystemInfo = Schemas['SystemInfo'];
export type HealthStatus = Schemas['HealthStatus'];
export type Problem = Schemas['Problem'];
export type FieldError = Schemas['FieldError'];
export type ErrorCode = Schemas['ErrorCode'];
export type AccountScope = Schemas['AccountScope'];
export type PermissionCode = Schemas['PermissionCode'];
export type PermissionScope = Schemas['PermissionScope'];
export type PermissionInfo = Schemas['PermissionInfo'];
export type LoginRequest = Schemas['LoginRequest'];
export type LoginResponse = Schemas['LoginResponse'];
export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];
export type CurrentAccount = Schemas['CurrentAccount'];
export type TenantSignupRequest = Schemas['TenantSignupRequest'];
export type TenantSignupResponse = Schemas['TenantSignupResponse'];
export type Role = Schemas['Role'];
export type CreateRoleRequest = Schemas['CreateRoleRequest'];
export type UpdateRoleRequest = Schemas['UpdateRoleRequest'];

/** Paths of the contract, relative to API_BASE_URL. */
export const ApiPaths = {
  systemInfo: '/system/info',
  me: '/me',
  login: '/auth/login',
  changePassword: '/auth/password',
  signup: '/tenants/signup',
  permissions: '/permissions',
  roles: '/roles',
  platformPermissions: '/platform/permissions',
} as const satisfies Record<string, keyof paths>;

/** Path of a single role (GET, PUT, DELETE /roles/{id}). */
export function rolePath(id: string): `/roles/${string}` {
  return `/roles/${encodeURIComponent(id)}`;
}
