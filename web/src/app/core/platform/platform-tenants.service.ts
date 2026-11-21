import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreatePurgeRequest,
  CreateTenantRequest,
  OwnerInvitation,
  PlanChange,
  PlanChangeRequest,
  PlanChangePreview,
  PlatformPaths,
  PriceLockRequest,
  PurgeRequest,
  Subscription,
  SubscriptionStatus,
  Tenant,
  TenantPage,
  TransitionRequest,
  confirmPurgePath,
  platformTenantActionPath,
  platformTenantPath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

export interface TenantQuery {
  q?: string;
  status?: SubscriptionStatus;
  page?: number;
  size?: number;
}

/** Idempotency of a creation: the same key and client id are sent again on a retry. */
export interface PlatformCreationKey {
  id: string;
  idempotencyKey: string;
}

export function newPlatformCreationKey(): PlatformCreationKey {
  return { id: crypto.randomUUID(), idempotencyKey: crypto.randomUUID() };
}

function idempotency(key: PlatformCreationKey): { headers: HttpHeaders } {
  return { headers: new HttpHeaders({ 'Idempotency-Key': key.idempotencyKey }) };
}

/**
 * Clinics (tenants) and their subscriptions, managed by the SUPER_ADMIN
 * (contracts/platform.openapi.yaml, platform.tenants.* permissions). The api enforces every rule.
 */
@Injectable({ providedIn: 'root' })
export class PlatformTenantsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /platform/tenants: search by name or RUC, filter by subscription status, paging. */
  list(query: TenantQuery = {}): Observable<TenantPage> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(name, String(value));
      }
    }
    return this.http.get<TenantPage>(this.config.apiUrl(PlatformPaths.tenants), { params });
  }

  /** POST /platform/tenants: clinic, subscription and OWNER invitation in one step. */
  create(request: Omit<CreateTenantRequest, 'id'>, key: PlatformCreationKey): Observable<Tenant> {
    return this.http.post<Tenant>(
      this.config.apiUrl(PlatformPaths.tenants),
      { id: key.id, ...request },
      idempotency(key),
    );
  }

  /** GET /platform/tenants/{tenantId}. */
  get(id: string): Observable<Tenant> {
    return this.http.get<Tenant>(this.config.apiUrl(platformTenantPath(id)));
  }

  /** POST .../owner-invitations: new one-time invitation; the previous one is revoked. */
  resendOwnerInvitation(
    id: string,
    email: string | null,
    key: PlatformCreationKey,
  ): Observable<OwnerInvitation> {
    return this.http.post<OwnerInvitation>(
      this.config.apiUrl(platformTenantActionPath(id, 'owner-invitations')),
      { id: key.id, ...(email ? { email } : {}) },
      idempotency(key),
    );
  }

  /** POST .../owner/two-factor-reset (204): the OWNER enrolls again on the next login. */
  resetOwnerTwoFactor(id: string, reason: string): Observable<void> {
    return this.http.post<void>(
      this.config.apiUrl(platformTenantActionPath(id, 'owner/two-factor-reset')),
      { reason },
    );
  }

  /** GET .../subscription: contracted price, dates, history and allowed manual targets. */
  subscription(id: string): Observable<Subscription> {
    return this.http.get<Subscription>(
      this.config.apiUrl(platformTenantActionPath(id, 'subscription')),
    );
  }

  /** POST .../subscription/plan-change-preview: changes nothing. */
  previewPlanChange(
    id: string,
    request: Pick<PlanChangeRequest, 'targetPlanId' | 'billingPeriod'>,
  ): Observable<PlanChangePreview> {
    return this.http.post<PlanChangePreview>(
      this.config.apiUrl(platformTenantActionPath(id, 'subscription/plan-change-preview')),
      request,
    );
  }

  /** POST .../subscription/plan-changes: upgrade now, downgrade or period change at renewal. */
  changePlan(
    id: string,
    request: Omit<PlanChangeRequest, 'id'>,
    key: PlatformCreationKey,
  ): Observable<PlanChange> {
    return this.http.post<PlanChange>(
      this.config.apiUrl(platformTenantActionPath(id, 'subscription/plan-changes')),
      { id: key.id, ...request },
      idempotency(key),
    );
  }

  /** POST .../subscription/transitions: manual transition, reactivation included. */
  transition(id: string, request: TransitionRequest): Observable<Subscription> {
    return this.http.post<Subscription>(
      this.config.apiUrl(platformTenantActionPath(id, 'subscription/transitions')),
      request,
    );
  }

  /** PUT .../subscription/price-lock: `lockedUntil` null removes the lock. */
  setPriceLock(id: string, request: PriceLockRequest): Observable<Subscription> {
    return this.http.put<Subscription>(
      this.config.apiUrl(platformTenantActionPath(id, 'subscription/price-lock')),
      request,
    );
  }

  /** POST .../purge-requests (platform.tenants.purge): first step, nothing is deleted. */
  requestPurge(
    id: string,
    request: Omit<CreatePurgeRequest, 'id'>,
    key: PlatformCreationKey,
  ): Observable<PurgeRequest> {
    return this.http.post<PurgeRequest>(
      this.config.apiUrl(platformTenantActionPath(id, 'purge-requests')),
      { id: key.id, ...request },
      idempotency(key),
    );
  }

  /** POST .../purge-requests/{requestId}/confirm (platform.tenants.purge): typing the RUC. */
  confirmPurge(id: string, requestId: string, rucConfirmation: string): Observable<PurgeRequest> {
    return this.http.post<PurgeRequest>(this.config.apiUrl(confirmPurgePath(id, requestId)), {
      rucConfirmation,
    });
  }
}
