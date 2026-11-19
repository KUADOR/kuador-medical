import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiPaths, SystemInfo } from '../api/api-types';
import { AppConfigService } from '../config/app-config.service';

@Injectable({ providedIn: 'root' })
export class SystemInfoService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  /** GET /system/info (public). */
  get(): Observable<SystemInfo> {
    return this.http.get<SystemInfo>(this.config.apiUrl(ApiPaths.systemInfo));
  }
}
