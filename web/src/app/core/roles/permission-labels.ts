import type { PermissionInfo, PermissionScope } from '../api/api-types';

/**
 * Spanish labels for the permission catalog. Unknown codes fall back to the description the
 * API sends, so a new permission still shows up before it gets a label here.
 */
export const PERMISSION_LABELS: Readonly<Record<string, string>> = {
  'roles.read': 'Ver roles y el catálogo de permisos',
  'roles.create': 'Crear roles',
  'roles.update': 'Editar roles',
  'roles.delete': 'Eliminar roles',
  'identity.users.read': 'Ver el personal de la clínica',
  'identity.users.manage': 'Invitar, editar, deshabilitar y habilitar al personal',
  'identity.assignments.manage': 'Asignar roles por sucursal',
  'patients.read': 'Ver pacientes',
  'patients.register': 'Registrar pacientes y su consentimiento',
  'patients.edit': 'Editar datos de pacientes',
  'patients.guardians.manage': 'Gestionar representantes de pacientes',
  'patients.selfRegistration.configure': 'Habilitar o deshabilitar el autorregistro de pacientes',
  'platform.permissions.read': 'Ver el catálogo completo de permisos de la plataforma',
};

export const PERMISSION_SCOPE_LABELS: Record<PermissionScope, string> = {
  TENANT: 'Clínica',
  PLATFORM: 'Plataforma',
};

/** Spanish names of the modules that group the catalog in the role editor. */
export const MODULE_LABELS: Readonly<Record<string, string>> = {
  identity: 'Personal, roles y accesos',
  patients: 'Pacientes',
  other: 'Otros permisos',
};

/** Accepts entries of both catalogs: clinic roles (typed codes) and the platform (free string). */
export function permissionLabel(permission: { code: string; description?: string }) {
  return PERMISSION_LABELS[permission.code] ?? permission.description ?? permission.code;
}

export function moduleLabel(module: string): string {
  return MODULE_LABELS[module] ?? module;
}

export interface PermissionGroup {
  module: string;
  label: string;
  permissions: PermissionInfo[];
}

/** Groups the catalog by module, keeping the order in which the API lists modules and codes. */
export function groupByModule(catalog: readonly PermissionInfo[]): PermissionGroup[] {
  const groups = new Map<string, PermissionGroup>();
  for (const permission of catalog) {
    const module = permission.module || 'other';
    let group = groups.get(module);
    if (!group) {
      group = { module, label: moduleLabel(module), permissions: [] };
      groups.set(module, group);
    }
    group.permissions.push(permission);
  }
  return [...groups.values()];
}
