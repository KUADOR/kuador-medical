import type { IconName } from '../../design-system';
import type { AccountKind, CurrentSession, SessionPermission } from '../api/api-types';
import { AuthRoutes } from './auth-routes';

/** Client routes of the authenticated pages. */
export const AppRoutes = {
  status: '/status',
  signup: '/signup',
  staff: '/staff',
  newStaffUser: '/staff/new',
  roles: '/roles',
  newRole: '/roles/new',
  platformPermissions: '/platform/permissions',
  platformTenants: '/platform/tenants',
  newPlatformTenant: '/platform/tenants/new',
  patients: '/patients',
  newPatient: '/patients/new',
  selfRegistrationSettings: '/patients/self-registration',
  myGuardianLinks: '/my-contacts',
} as const;

/** Detail page of a patient. */
export function patientUrl(id: string): string {
  return `${AppRoutes.patients}/${encodeURIComponent(id)}`;
}

/** Edit page of a patient. */
export function editPatientUrl(id: string): string {
  return `${patientUrl(id)}/edit`;
}

/** Detail page of a clinic in the SUPER_ADMIN panel. */
export function platformTenantUrl(id: string): string {
  return `${AppRoutes.platformTenants}/${encodeURIComponent(id)}`;
}

/** Modules of the clinic plan (CurrentSession.enabledModules). */
export const Modules = {
  identity: 'identity',
  patients: 'patients',
} as const;

/** What a page or menu entry needs; the API enforces it again (ADR-008, Constitution VII). */
export interface AccessRequirement {
  /** Every permission listed must be granted by the active assignment. */
  permissions?: readonly SessionPermission[];
  /** Module that the clinic plan must enable. Platform pages do not need one. */
  module?: string;
  /** Account kinds allowed (e.g. only PATIENT accounts). Any kind when absent. */
  kinds?: readonly AccountKind[];
}

export interface MenuItem extends AccessRequirement {
  label: string;
  link: string;
  icon: IconName;
}

/**
 * Menu entries of authenticated accounts, filtered by the permissions and modules of GET /me.
 * The order is the order of the menu, and the first allowed entry is the landing page: the
 * patient search goes first so the staff reach it without extra steps (MEDK-42, FR-007).
 */
export const MENU_ITEMS: readonly MenuItem[] = [
  {
    label: 'Pacientes',
    link: AppRoutes.patients,
    icon: 'search',
    permissions: ['patients.read'],
    module: Modules.patients,
  },
  {
    label: 'Personal',
    link: AppRoutes.staff,
    icon: 'users',
    permissions: ['identity.users.read'],
    module: Modules.identity,
  },
  {
    label: 'Roles',
    link: AppRoutes.roles,
    icon: 'lock',
    permissions: ['roles.read'],
    module: Modules.identity,
  },
  {
    label: 'Mis contactos autorizados',
    link: AppRoutes.myGuardianLinks,
    icon: 'user-plus',
    kinds: ['PATIENT'],
  },
  {
    label: 'Clínicas',
    link: AppRoutes.platformTenants,
    icon: 'building-2',
    permissions: ['platform.tenants.manage'],
  },
  {
    label: 'Catálogo de permisos',
    link: AppRoutes.platformPermissions,
    icon: 'clipboard-list',
    permissions: ['platform.permissions.read'],
  },
];

/** True when the session grants every permission and the module of the requirement. */
export function canAccess(account: CurrentSession | null, requirement: AccessRequirement): boolean {
  if (!account) {
    return false;
  }
  const granted = new Set(account.permissions);
  const permissionsOk = (requirement.permissions ?? []).every((code) => granted.has(code));
  const moduleOk =
    !requirement.module || (account.enabledModules ?? []).includes(requirement.module);
  const kindOk = !requirement.kinds || requirement.kinds.includes(account.kind);
  return permissionsOk && moduleOk && kindOk;
}

/** Name of the protected clinic-wide role (Role.systemKey and CurrentSession.roles). */
export const OWNER_ROLE_NAME = 'OWNER';

/**
 * True when the active assignment includes the OWNER role. Only an OWNER edits ADMIN and assigns
 * OWNER; the UI uses it to offer those actions and the API enforces them (ADR-013).
 */
export function isOwner(account: CurrentSession | null): boolean {
  return account?.roles.includes(OWNER_ROLE_NAME) ?? false;
}

export function menuFor(account: CurrentSession | null): MenuItem[] {
  if (!account || account.mustChangePassword) {
    return [];
  }
  return MENU_ITEMS.filter((item) => canAccess(account, item));
}

/** First page for an account: pending password change first, then its first menu entry. */
export function landingUrl(account: CurrentSession | null): string {
  if (!account) {
    return AuthRoutes.login;
  }
  if (account.mustChangePassword) {
    return AuthRoutes.changePassword;
  }
  return menuFor(account)[0]?.link ?? AppRoutes.status;
}

/** Routes that never make sense as a return URL after logging in. */
const NO_RETURN_PREFIXES: readonly string[] = [
  AuthRoutes.login,
  AuthRoutes.changePassword,
  AuthRoutes.forgotPassword,
  AuthRoutes.resetPassword,
  AuthRoutes.invitation,
  AuthRoutes.verifyEmail,
];

/**
 * Only follows in-app return URLs ("/roles"), never absolute or protocol-relative ones,
 * so a crafted ?returnUrl= cannot send people to another site.
 */
export function safeReturnUrl(value: string | null | undefined): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return null;
  }
  if (NO_RETURN_PREFIXES.some((prefix) => value.startsWith(prefix))) {
    return null;
  }
  return value;
}
