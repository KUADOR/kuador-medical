import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ClinicPaths,
  CreateRoleRequest,
  PermissionInfo,
  Role,
  UpdateRoleRequest,
  rolePath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

/**
 * True for the protected OWNER role. A system role without `systemKey` (the identity contract the
 * api serves until MEDK-14 lands) is treated as OWNER too, so it never looks editable.
 */
export function isOwnerRole(role: Pick<Role, 'systemKey' | 'systemRole'>): boolean {
  return role.systemKey === 'OWNER' || (role.systemRole && !role.systemKey);
}

/** Roles of the clinic resolved from the token (ADR-007): no request carries a tenant id. */
@Injectable({ providedIn: 'root' })
export class RolesService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /roles (roles.read). */
  list(): Observable<Role[]> {
    return this.http.get<Role[]>(this.config.apiUrl(ClinicPaths.roles));
  }

  /** GET /roles/{id} (roles.read). */
  get(id: string): Observable<Role> {
    return this.http.get<Role>(this.config.apiUrl(rolePath(id)));
  }

  /** POST /roles (roles.create). */
  create(request: CreateRoleRequest): Observable<Role> {
    return this.http.post<Role>(this.config.apiUrl(ClinicPaths.roles), request);
  }

  /**
   * PUT /roles/{id} (roles.update), with the version read for optimistic locking.
   * OWNER is immutable and only an OWNER edits ADMIN; the API enforces both.
   */
  update(id: string, request: UpdateRoleRequest): Observable<Role> {
    return this.http.put<Role>(this.config.apiUrl(rolePath(id)), request);
  }

  /** DELETE /roles/{id} (roles.delete). OWNER and ADMIN are never deletable. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(this.config.apiUrl(rolePath(id)));
  }

  /** GET /permissions (roles.read): clinic-scoped catalog assignable to roles, with its module. */
  assignablePermissions(): Observable<PermissionInfo[]> {
    return this.http.get<PermissionInfo[]>(this.config.apiUrl(ClinicPaths.permissions));
  }
}
