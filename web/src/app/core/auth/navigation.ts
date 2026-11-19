import type { CurrentAccount, PermissionCode } from '../api/api-types';
import { AuthRoutes } from './auth-routes';

/** Client routes of the authenticated pages. */
export const AppRoutes = {
  status: '/status',
  signup: '/signup',
  roles: '/roles',
  newRole: '/roles/new',
  platformPermissions: '/platform/permissions',
} as const;

export interface MenuItem {
  label: string;
  link: string;
  /** Permission needed to show the item; the API enforces it again (ADR-008). */
  permission: PermissionCode;
}

/** Menu entries of authenticated accounts, filtered by the permissions of GET /me. */
export const MENU_ITEMS: readonly MenuItem[] = [
  { label: 'Roles', link: AppRoutes.roles, permission: 'roles.read' },
  {
    label: 'Catálogo de permisos',
    link: AppRoutes.platformPermissions,
    permission: 'platform.permissions.read',
  },
];

export function menuFor(account: CurrentAccount | null): MenuItem[] {
  if (!account || account.mustChangePassword) {
    return [];
  }
  const granted = new Set(account.permissions);
  return MENU_ITEMS.filter((item) => granted.has(item.permission));
}

/** First page for an account: pending password change first, then its first menu entry. */
export function landingUrl(account: CurrentAccount | null): string {
  if (!account) {
    return AuthRoutes.login;
  }
  if (account.mustChangePassword) {
    return AuthRoutes.changePassword;
  }
  return menuFor(account)[0]?.link ?? AppRoutes.status;
}

/**
 * Only follows in-app return URLs ("/roles"), never absolute or protocol-relative ones,
 * so a crafted ?returnUrl= cannot send people to another site.
 */
export function safeReturnUrl(value: string | null | undefined): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return null;
  }
  if (value.startsWith(AuthRoutes.login) || value.startsWith(AuthRoutes.changePassword)) {
    return null;
  }
  return value;
}
