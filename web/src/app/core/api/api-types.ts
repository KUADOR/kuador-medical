// Friendly aliases over the types generated from the contracts.
// Regenerate with `npm run api:generate`; never edit the *.schema.d.ts files by hand.
//
// - identity.schema.d.ts (contracts/identity.openapi.yaml): operations the api serves today
//   that the user registration contract does not replace (status, clinic signup, password
//   change, platform catalog).
// - user-registration.schema.d.ts (contracts/user-registration.openapi.yaml): target contract
//   of MEDK-12. Authentication and session (login, clinic and branch selection, refresh,
//   logout, password reset, invitations, email verification, switch-branch, GET /me and
//   patient self-registration), staff users, branch role assignments and roles use it.
// - platform.schema.d.ts (contracts/platform.openapi.yaml): target contract of MEDK-49. Plans,
//   prices, catalogs and platform settings of the SUPER_ADMIN (MEDK-72) use it, and its
//   ErrorCode, a superset of the user registration one.
import type { components as IdentityComponents, paths as IdentityPaths } from './identity.schema';
import type {
  components as RegistrationComponents,
  paths as RegistrationPaths,
} from './user-registration.schema';
import type {
  components as PlatformComponents,
  paths as PlatformContractPaths,
} from './platform.schema';

type Schemas = IdentityComponents['schemas'];
type Registration = RegistrationComponents['schemas'];
type Platform = PlatformComponents['schemas'];

// ---------------------------------------------------------------- identity contract
export type SystemInfo = Schemas['SystemInfo'];
export type HealthStatus = Schemas['HealthStatus'];
export type PermissionScope = Schemas['PermissionScope'];
/** Entry of the whole platform catalog (GET /platform/permissions); it carries no module. */
export type PlatformPermissionInfo = Schemas['PermissionInfo'];
export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];
export type TenantSignupRequest = Schemas['TenantSignupRequest'];
export type TenantSignupResponse = Schemas['TenantSignupResponse'];

// ---------------------------------------------------------------- user registration contract
// Errors and permission codes come from the target contract: it is a superset of identity.
// The platform contract (MEDK-49) extends the error codes: its ErrorCode is a superset.
export type Problem = Platform['Problem'];
export type FieldError = Platform['FieldError'];
export type ErrorCode = Platform['ErrorCode'];
/**
 * Permission codes added by the platform contract (its description, § Permissions). They are
 * not an enum of any contract yet: MEDK-65 adds them to the `Permission` catalog of the api.
 */
export type PlatformManagementPermission =
  | 'platform.plans.manage'
  | 'platform.tenants.manage'
  | 'platform.tenants.purge'
  | 'platform.settings.manage'
  | 'subscription.read'
  | 'tenant.data.export';
export type PermissionCode = Registration['PermissionCode'];
/** Any permission a page or menu entry can require, including the platform epic ones. */
export type AccessPermission = PermissionCode | PlatformManagementPermission;
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
// Roles and permission catalog of the clinic.
export type PermissionInfo = Registration['PermissionInfo'];
export type Role = Registration['Role'];
export type RoleSummary = Registration['RoleSummary'];
export type CreateRoleRequest = Registration['CreateRoleRequest'];
export type UpdateRoleRequest = Registration['UpdateRoleRequest'];
// Staff users and their branch role assignments.
export type AccountStatus = Registration['AccountStatus'];
export type StaffUser = Registration['StaffUser'];
export type StaffUserPage = Registration['StaffUserPage'];
export type CreateStaffUserRequest = Registration['CreateStaffUserRequest'];
export type UpdateStaffUserRequest = Registration['UpdateStaffUserRequest'];
export type RoleAssignmentsRequest = Registration['RoleAssignmentsRequest'];
export type RoleAssignment = Registration['RoleAssignment'];

// Patients, guardians and self-registration settings (MEDK-22).
export type Patient = Registration['Patient'];
export type PatientPage = Registration['PatientPage'];
export type PatientAccountStatus = Registration['PatientAccountStatus'];
export type RegisterPatientRequest = Registration['RegisterPatientRequest'];
export type UpdatePatientRequest = Registration['UpdatePatientRequest'];
export type ConsentGrantor = Registration['ConsentGrantor'];
export type Consent = Registration['Consent'];
export type RecordConsentRequest = Registration['RecordConsentRequest'];
export type GuardianPersonInput = Registration['GuardianPersonInput'];
export type GuardianInput = Registration['GuardianInput'];
export type LinkType = Registration['LinkType'];
export type GuardianLink = Registration['GuardianLink'];
export type CreateGuardianLinkRequest = Registration['CreateGuardianLinkRequest'];
export type AuthorizeLinkRequest = Registration['AuthorizeLinkRequest'];
export type PatientInvitationRequest = Registration['PatientInvitationRequest'];
export type RegistrationSettings = Registration['RegistrationSettings'];
export type UpdateRegistrationSettingsRequest = Registration['UpdateRegistrationSettingsRequest'];

// ---------------------------------------------------------------- platform contract (MEDK-49)
export type ModuleCode = Platform['ModuleCode'];
export type LimitCode = Platform['LimitCode'];
export type PlanLimits = Platform['PlanLimits'];
export type BillingPeriod = Platform['BillingPeriod'];
export type PlanStatus = Platform['PlanStatus'];
export type ModuleInfo = Platform['ModuleInfo'];
export type LimitInfo = Platform['LimitInfo'];
export type PlanPrice = Platform['PlanPrice'];
export type PriceInput = Platform['PriceInput'];
export type CreatePlanPriceRequest = Platform['CreatePlanPriceRequest'];
export type Plan = Platform['Plan'];
export type CreatePlanRequest = Platform['CreatePlanRequest'];
export type UpdatePlanRequest = Platform['UpdatePlanRequest'];
export type PlatformSettings = Platform['PlatformSettings'];

/** Paths of the identity contract, relative to API_BASE_URL. */
export const ApiPaths = {
  systemInfo: '/system/info',
  changePassword: '/auth/password',
  signup: '/tenants/signup',
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

/** Paths of the user registration contract for staff users, assignments and roles. */
export const ClinicPaths = {
  permissions: '/permissions',
  roles: '/roles',
  staffUsers: '/staff-users',
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

/** Path of a single staff user (GET, PATCH /staff-users/{id}). */
export function staffUserPath(id: string): `/staff-users/${string}` {
  return `/staff-users/${encodeURIComponent(id)}`;
}

/** Actions on a staff user: resend invitation, disable, enable and replace role assignments. */
export type StaffUserAction = 'invitation' | 'disable' | 'enable' | 'role-assignments';

export function staffUserActionPath(
  id: string,
  action: StaffUserAction,
): `/staff-users/${string}/${StaffUserAction}` {
  return `/staff-users/${encodeURIComponent(id)}/${action}`;
}

/** Paths of the user registration contract used by the patient pages (MEDK-22). */
export const PatientPaths = {
  patients: '/patients',
  myGuardianLinks: '/me/guardian-links',
  registrationSettings: '/patient-registration-settings',
} as const satisfies Record<string, keyof RegistrationPaths>;

/** Path of a single patient (GET, PATCH /patients/{id}). */
export function patientPath(id: string): `/patients/${string}` {
  return `/patients/${encodeURIComponent(id)}`;
}

/** Sub-resource of a patient: consents, guardian-links or account-invitation. */
export function patientSubPath(
  id: string,
  resource: 'consents' | 'guardian-links' | 'account-invitation',
): `/patients/${string}/${typeof resource}` {
  return `${patientPath(id)}/${resource}`;
}

/** POST /patients/{id}/guardian-links/{linkId}/end. */
export function endGuardianLinkPath(
  id: string,
  linkId: string,
): `/patients/${string}/guardian-links/${string}/end` {
  return `${patientPath(id)}/guardian-links/${encodeURIComponent(linkId)}/end`;
}

/** Public self-registration of a clinic (POST /public/clinics/{publicCode}/patient-registrations). */
export function selfRegistrationPath(
  publicCode: string,
): `/public/clinics/${string}/patient-registrations` {
  return `/public/clinics/${encodeURIComponent(publicCode)}/patient-registrations`;
}

/** Paths of the platform contract for plans, catalogs and settings (MEDK-72). */
export const PlatformPaths = {
  plans: '/platform/plans',
  moduleCatalog: '/platform/catalog/modules',
  limitCatalog: '/platform/catalog/limits',
  settings: '/platform/settings',
} as const satisfies Record<string, keyof PlatformContractPaths>;

/** Path of a single plan (GET, PUT /platform/plans/{planId}). */
export function planPath(id: string): `/platform/plans/${string}` {
  return `${PlatformPaths.plans}/${encodeURIComponent(id)}`;
}

/** POST /platform/plans/{planId}/archive and GET, POST /platform/plans/{planId}/prices. */
export function planSubPath(
  id: string,
  resource: 'archive' | 'prices',
): `/platform/plans/${string}/${typeof resource}` {
  return `${planPath(id)}/${resource}`;
}

// ---------------------------------------------------------------- platform contract: OWNER (MEDK-74)
export type OwnerInvitationPreview = Platform['OwnerInvitationPreview'];
export type OwnerInvitationTokenRequest = Platform['OwnerInvitationTokenRequest'];
export type AcceptOwnerInvitationRequest = Platform['AcceptOwnerInvitationRequest'];
/** Tokens returned by the invitation and second factor operations (shape of AuthenticatedResult). */
export type PlatformSession = Platform['Session'];
export type TwoFactorEnrollment = Platform['TwoFactorEnrollment'];
export type TwoFactorCodeRequest = Platform['TwoFactorCodeRequest'];
/** What a session that has not passed the second factor must do (Problem.twoFactor). */
export type TwoFactorStep = NonNullable<Problem['twoFactor']>;
export type SubscriptionStatus = Platform['SubscriptionStatus'];
export type AccessMode = Platform['AccessMode'];
export type NoticeKind = Platform['NoticeKind'];
export type SubscriptionNotice = Platform['Notice'];
export type LimitUsage = Platform['LimitUsage'];
export type ClinicSubscription = Platform['ClinicSubscription'];
export type ExportFormat = Platform['ExportFormat'];
export type ExportStatus = Platform['ExportStatus'];
export type CreateExportRequest = Platform['CreateExportRequest'];
export type TenantExport = Platform['TenantExport'];

/** Paths of the platform contract used by the OWNER flows (MEDK-74). */
export const OwnerPaths = {
  invitationPreview: '/public/owner-invitations/preview',
  invitationAccept: '/public/owner-invitations/accept',
  twoFactorEnrollment: '/auth/two-factor/enrollment',
  twoFactorEnrollmentConfirm: '/auth/two-factor/enrollment/confirm',
  twoFactorVerify: '/auth/two-factor/verify',
  subscription: '/tenant/subscription',
  exports: '/tenant/exports',
} as const satisfies Record<string, keyof PlatformContractPaths>;

/** GET /tenant/exports/{exportId}. */
export function tenantExportPath(id: string): `/tenant/exports/${string}` {
  return `${OwnerPaths.exports}/${encodeURIComponent(id)}`;
}
