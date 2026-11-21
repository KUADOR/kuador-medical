import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BillingPeriod,
  CreatePlanPriceRequest,
  CreatePlanRequest,
  LimitInfo,
  ModuleInfo,
  Plan,
  PlanPrice,
  PlanStatus,
  PlatformPaths,
  PlatformSettings,
  UpdatePlanRequest,
  planPath,
  planSubPath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';
import { CreationKey } from '../staff/staff-users.service';

/**
 * Plans, prices, catalogs and platform settings of the SUPER_ADMIN (platform contract,
 * MEDK-49). Platform data has no tenant: these requests never carry a clinic id.
 */
@Injectable({ providedIn: 'root' })
export class PlansService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /platform/plans (platform.plans.manage), optionally filtered by status. */
  list(status?: PlanStatus): Observable<Plan[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<Plan[]>(this.config.apiUrl(PlatformPaths.plans), { params });
  }

  /** GET /platform/plans/{planId}. */
  get(id: string): Observable<Plan> {
    return this.http.get<Plan>(this.config.apiUrl(planPath(id)));
  }

  /** POST /platform/plans: a retry sends the same key and client id. */
  create(request: Omit<CreatePlanRequest, 'id'>, key: CreationKey): Observable<Plan> {
    return this.http.post<Plan>(
      this.config.apiUrl(PlatformPaths.plans),
      { id: key.id, ...request },
      { headers: new HttpHeaders({ 'Idempotency-Key': key.idempotencyKey }) },
    );
  }

  /** PUT /platform/plans/{planId}, with the version read (prices are not changed here). */
  update(id: string, request: UpdatePlanRequest): Observable<Plan> {
    return this.http.put<Plan>(this.config.apiUrl(planPath(id)), request);
  }

  /** POST /platform/plans/{planId}/archive. Plans are never deleted. */
  archive(id: string): Observable<Plan> {
    return this.http.post<Plan>(this.config.apiUrl(planSubPath(id, 'archive')), null);
  }

  /** GET /platform/plans/{planId}/prices: every price version, newest first. */
  prices(id: string, billingPeriod?: BillingPeriod): Observable<PlanPrice[]> {
    const params = billingPeriod ? new HttpParams().set('billingPeriod', billingPeriod) : undefined;
    return this.http.get<PlanPrice[]>(this.config.apiUrl(planSubPath(id, 'prices')), { params });
  }

  /** POST /platform/plans/{planId}/prices: a new immutable price version. */
  createPrice(
    id: string,
    request: Omit<CreatePlanPriceRequest, 'id'>,
    key: CreationKey,
  ): Observable<PlanPrice> {
    return this.http.post<PlanPrice>(
      this.config.apiUrl(planSubPath(id, 'prices')),
      { id: key.id, ...request },
      { headers: new HttpHeaders({ 'Idempotency-Key': key.idempotencyKey }) },
    );
  }

  /** GET /platform/catalog/modules: modules a plan can enable, with their Spanish label. */
  modules(): Observable<ModuleInfo[]> {
    return this.http.get<ModuleInfo[]>(this.config.apiUrl(PlatformPaths.moduleCatalog));
  }

  /** GET /platform/catalog/limits: limits a plan can set, with unit and Spanish label. */
  limits(): Observable<LimitInfo[]> {
    return this.http.get<LimitInfo[]>(this.config.apiUrl(PlatformPaths.limitCatalog));
  }
}

/** Platform settings: configurable periods of the subscription lifecycle (plan D10). */
@Injectable({ providedIn: 'root' })
export class PlatformSettingsService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /platform/settings (platform.settings.manage). */
  get(): Observable<PlatformSettings> {
    return this.http.get<PlatformSettings>(this.config.apiUrl(PlatformPaths.settings));
  }

  /** PUT /platform/settings, with the version read for optimistic locking. */
  update(settings: PlatformSettings): Observable<PlatformSettings> {
    return this.http.put<PlatformSettings>(this.config.apiUrl(PlatformPaths.settings), settings);
  }
}
