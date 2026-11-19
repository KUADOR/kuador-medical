import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApiPaths,
  CreateRoleRequest,
  PermissionInfo,
  Role,
  UpdateRoleRequest,
  rolePath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

/** Roles of the clinic resolved from the token (ADR-007): no request carries a tenant id. */
@Injectable({ providedIn: 'root' })
export class RolesService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /roles (roles.read). */
  list(): Observable<Role[]> {
    return this.http.get<Role[]>(this.config.apiUrl(ApiPaths.roles));
  }

  /** GET /roles/{id} (roles.read). */
  get(id: string): Observable<Role> {
    return this.http.get<Role>(this.config.apiUrl(rolePath(id)));
  }

  /** POST /roles (roles.create). */
  create(request: CreateRoleRequest): Observable<Role> {
    return this.http.post<Role>(this.config.apiUrl(ApiPaths.roles), request);
  }

  /** PUT /roles/{id} (roles.update), with the version read for optimistic locking. */
  update(id: string, request: UpdateRoleRequest): Observable<Role> {
    return this.http.put<Role>(this.config.apiUrl(rolePath(id)), request);
  }

  /** DELETE /roles/{id} (roles.delete). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(this.config.apiUrl(rolePath(id)));
  }

  /** GET /permissions (roles.read): clinic-scoped catalog assignable to roles. */
  assignablePermissions(): Observable<PermissionInfo[]> {
    return this.http.get<PermissionInfo[]>(this.config.apiUrl(ApiPaths.permissions));
  }
}
