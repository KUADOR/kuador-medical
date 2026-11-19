import type { CurrentSession, PermissionCode } from '../api/api-types';
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
} as const;

/** Modules of the clinic plan (CurrentSession.enabledModules). */
export const Modules = {
  identity: 'identity',
  patients: 'patients',
} as const;

/** What a page or menu entry needs; the API enforces it again (ADR-008, Constitution VII). */
export interface AccessRequirement {
  /** Every permission listed must be granted by the active assignment. */
  permissions?: readonly PermissionCode[];
  /** Module that the clinic plan must enable. Platform pages do not need one. */
  module?: string;
}

export interface MenuItem extends AccessRequirement {
  label: string;
  link: string;
}

/** Menu entries of authenticated accounts, filtered by the permissions and modules of GET /me. */
export const MENU_ITEMS: readonly MenuItem[] = [
  {
    label: 'Personal',
    link: AppRoutes.staff,
    permissions: ['identity.users.read'],
    module: Modules.identity,
  },
  { label: 'Roles', link: AppRoutes.roles, permissions: ['roles.read'], module: Modules.identity },
  {
    label: 'Catálogo de permisos',
    link: AppRoutes.platformPermissions,
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
  return permissionsOk && moduleOk;
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
