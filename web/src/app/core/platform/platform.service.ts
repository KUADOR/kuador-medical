import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiPaths, PlatformPermissionInfo } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

/** Platform-level endpoints of the SUPER_ADMIN (PLATFORM scope only). */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /platform/permissions (platform.permissions.read): full catalog. */
  permissions(): Observable<PlatformPermissionInfo[]> {
    return this.http.get<PlatformPermissionInfo[]>(
      this.config.apiUrl(ApiPaths.platformPermissions),
    );
  }
}
