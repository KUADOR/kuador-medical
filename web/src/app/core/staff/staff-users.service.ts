import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AccountStatus,
  ClinicPaths,
  CreateStaffUserRequest,
  RoleAssignment,
  RoleAssignmentsRequest,
  StaffUser,
  StaffUserPage,
  UpdateStaffUserRequest,
  staffUserActionPath,
  staffUserPath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

export interface StaffUserQuery {
  q?: string;
  status?: AccountStatus;
  branchId?: string;
  page?: number;
  size?: number;
}

/** Idempotency of a creation: the same key and client id are sent again on a retry. */
export interface CreationKey {
  id: string;
  idempotencyKey: string;
}

export function newCreationKey(): CreationKey {
  return { id: crypto.randomUUID(), idempotencyKey: crypto.randomUUID() };
}

/** Staff users of the clinic resolved from the token (ADR-007): no request carries a tenant id. */
@Injectable({ providedIn: 'root' })
export class StaffUsersService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /staff-users (identity.users.read). */
  list(query: StaffUserQuery = {}): Observable<StaffUserPage> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(name, String(value));
      }
    }
    return this.http.get<StaffUserPage>(this.config.apiUrl(ClinicPaths.staffUsers), { params });
  }

  /** GET /staff-users/{id} (identity.users.read). */
  get(id: string): Observable<StaffUser> {
    return this.http.get<StaffUser>(this.config.apiUrl(staffUserPath(id)));
  }

  /** POST /staff-users (identity.users.manage): creates the account and sends the invitation. */
  create(request: Omit<CreateStaffUserRequest, 'id'>, key: CreationKey): Observable<StaffUser> {
    return this.http.post<StaffUser>(
      this.config.apiUrl(ClinicPaths.staffUsers),
      { id: key.id, ...request },
      { headers: new HttpHeaders({ 'Idempotency-Key': key.idempotencyKey }) },
    );
  }

  /** PATCH /staff-users/{id} (identity.users.manage): only the given fields change. */
  update(id: string, request: UpdateStaffUserRequest): Observable<StaffUser> {
    return this.http.patch<StaffUser>(this.config.apiUrl(staffUserPath(id)), request);
  }

  /** POST /staff-users/{id}/invitation (identity.users.manage). */
  resendInvitation(id: string): Observable<void> {
    return this.http.post<void>(this.config.apiUrl(staffUserActionPath(id, 'invitation')), null);
  }

  /** POST /staff-users/{id}/disable (identity.users.manage). */
  disable(id: string): Observable<StaffUser> {
    return this.http.post<StaffUser>(this.config.apiUrl(staffUserActionPath(id, 'disable')), null);
  }

  /** POST /staff-users/{id}/enable (identity.users.manage). */
  enable(id: string): Observable<StaffUser> {
    return this.http.post<StaffUser>(this.config.apiUrl(staffUserActionPath(id, 'enable')), null);
  }

  /** PUT /staff-users/{id}/role-assignments (identity.assignments.manage). */
  replaceAssignments(id: string, request: RoleAssignmentsRequest): Observable<RoleAssignment[]> {
    return this.http.put<RoleAssignment[]>(
      this.config.apiUrl(staffUserActionPath(id, 'role-assignments')),
      request,
    );
  }
}
