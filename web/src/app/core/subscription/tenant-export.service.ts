import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CreateExportRequest,
  ExportFormat,
  OwnerPaths,
  TenantExport,
  tenantExportPath,
} from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

/**
 * Data export of the current clinic (platform contract): allowed also while the clinic is
 * read-only (SUSPENDED, or CANCELLED during retention). Audited by the API.
 */
@Injectable({ providedIn: 'root' })
export class TenantExportService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /**
   * POST /tenant/exports. The client id doubles as Idempotency-Key, so retrying after a network
   * error never starts a second export.
   */
  request(format: ExportFormat, id: string): Promise<TenantExport> {
    return firstValueFrom(
      this.http.post<TenantExport>(
        this.config.apiUrl(OwnerPaths.exports),
        { id, format } satisfies CreateExportRequest,
        { headers: { 'Idempotency-Key': id } },
      ),
    );
  }

  /** GET /tenant/exports/{exportId}. */
  get(id: string): Promise<TenantExport> {
    return firstValueFrom(this.http.get<TenantExport>(this.config.apiUrl(tenantExportPath(id))));
  }
}
