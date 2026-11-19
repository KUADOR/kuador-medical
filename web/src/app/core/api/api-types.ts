// Friendly aliases over the types generated from the contracts.
// Regenerate with `npm run api:generate`; never edit the *.schema.d.ts files by hand.
//
// - identity.schema.d.ts (contracts/identity.openapi.yaml): operations the api serves today
//   that the user registration contract does not replace (status, clinic signup, password
//   change, roles, platform catalog).
// - user-registration.schema.d.ts (contracts/user-registration.openapi.yaml): target contract
//   of MEDK-12. Authentication and session (login, clinic and branch selection, refresh,
//   logout, password reset, invitations, email verification, switch-branch, GET /me and
//   patient self-registration) use it.
import type { components as IdentityComponents, paths as IdentityPaths } from './identity.schema';
import type {
  components as RegistrationComponents,
  paths as RegistrationPaths,
} from './user-registration.schema';

type Schemas = IdentityComponents['schemas'];
type Registration = RegistrationComponents['schemas'];

// ---------------------------------------------------------------- identity contract
export type SystemInfo = Schemas['SystemInfo'];
export type HealthStatus = Schemas['HealthStatus'];
export type PermissionScope = Schemas['PermissionScope'];
export type PermissionInfo = Schemas['PermissionInfo'];
export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];
export type TenantSignupRequest = Schemas['TenantSignupRequest'];
export type TenantSignupResponse = Schemas['TenantSignupResponse'];
export type Role = Schemas['Role'];
export type CreateRoleRequest = Schemas['CreateRoleRequest'];
export type UpdateRoleRequest = Schemas['UpdateRoleRequest'];
/** Permission code as the identity role operations carry it (free string in that contract). */
export type RolePermissionCode = Schemas['PermissionCode'];

// ---------------------------------------------------------------- user registration contract
// Errors and permission codes come from the target contract: it is a superset of identity.
export type Problem = Registration['Problem'];
export type FieldError = Registration['FieldError'];
export type ErrorCode = Registration['ErrorCode'];
export type PermissionCode = Registration['PermissionCode'];
export type AccountScope = Registration['AccountScope'];
export type AccountKind = Registration['AccountKind'];
export type ClinicSummary = Registration['ClinicSummary'];
export type BranchSummary = Registration['BranchSummary'];
export type LoginRequest = Registration['LoginRequest'];
export type SelectClinicRequest = Registration['SelectClinicRequest'];
export type SelectBranchRequest = Registration['SelectBranchRequest'];
export type RefreshRequest = Registration['RefreshRequest'];
export type SwitchBranchRequest = Registration['SwitchBranchRequest'];
export type PasswordResetRequest = Registration['PasswordResetRequest'];
export type PasswordResetConfirmRequest = Registration['PasswordResetConfirmRequest'];
export type OneTimeTokenRequest = Registration['OneTimeTokenRequest'];
export type AcceptInvitationRequest = Registration['AcceptInvitationRequest'];
export type InvitationPreview = Registration['InvitationPreview'];
export type LoginResult = Registration['LoginResult'];
export type AuthenticatedResult = Registration['AuthenticatedResult'];
export type ClinicSelectionResult = Registration['ClinicSelectionResult'];
export type BranchSelectionResult = Registration['BranchSelectionResult'];
export type CurrentSession = Registration['CurrentSession'];
export type SelfRegistrationRequest = Registration['SelfRegistrationRequest'];

/** Paths of the identity contract, relative to API_BASE_URL. */
export const ApiPaths = {
  systemInfo: '/system/info',
  changePassword: '/auth/password',
  signup: '/tenants/signup',
  permissions: '/permissions',
  roles: '/roles',
  platformPermissions: '/platform/permissions',
} as const satisfies Record<string, keyof IdentityPaths>;

/** Paths of the user registration contract used by authentication and session. */
export const SessionPaths = {
  login: '/auth/login',
  loginClinic: '/auth/login/clinic',
  loginBranch: '/auth/login/branch',
  refresh: '/auth/refresh',
  logout: '/auth/logout',
  passwordResetRequest: '/auth/password-reset/request',
  passwordResetConfirm: '/auth/password-reset/confirm',
  invitationPreview: '/auth/invitations/preview',
  invitationAccept: '/auth/invitations/accept',
  emailVerification: '/auth/email-verification/confirm',
  switchBranch: '/auth/switch-branch',
  me: '/me',
} as const satisfies Record<string, keyof RegistrationPaths>;

/**
 * Public operations: they never carry the bearer token and a 401 there is a business error
 * (wrong credentials, expired pre-auth token), never a reason to refresh the session.
 */
export const PUBLIC_SESSION_PATHS: readonly string[] = [
  SessionPaths.login,
  SessionPaths.loginClinic,
  SessionPaths.loginBranch,
  SessionPaths.refresh,
  SessionPaths.logout,
  SessionPaths.passwordResetRequest,
  SessionPaths.passwordResetConfirm,
  SessionPaths.invitationPreview,
  SessionPaths.invitationAccept,
  SessionPaths.emailVerification,
];

/** Path of a single role (GET, PUT, DELETE /roles/{id}). */
export function rolePath(id: string): `/roles/${string}` {
  return `/roles/${encodeURIComponent(id)}`;
}

/** Public self-registration of a clinic (POST /public/clinics/{publicCode}/patient-registrations). */
export function selfRegistrationPath(
  publicCode: string,
): `/public/clinics/${string}/patient-registrations` {
  return `/public/clinics/${encodeURIComponent(publicCode)}/patient-registrations`;
}
