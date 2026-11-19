import type { PermissionInfo, PermissionScope } from '../api/api-types';

/**
 * Spanish labels for the permission catalog. The API describes permissions in English
 * (contract language); unknown codes fall back to that description so a new permission
 * still shows up before it gets a label here.
 */
export const PERMISSION_LABELS: Readonly<Record<string, string>> = {
  'roles.read': 'Ver roles y el catálogo de permisos',
  'roles.create': 'Crear roles',
  'roles.update': 'Editar roles',
  'roles.delete': 'Eliminar roles',
  'platform.permissions.read': 'Ver el catálogo completo de permisos de la plataforma',
};

export const PERMISSION_SCOPE_LABELS: Record<PermissionScope, string> = {
  TENANT: 'Clínica',
  PLATFORM: 'Plataforma',
};

export function permissionLabel(
  permission: Pick<PermissionInfo, 'code'> & { description?: string },
) {
  return PERMISSION_LABELS[permission.code] ?? permission.description ?? permission.code;
}
