import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Shape of public/config.json, read at runtime so one build serves every environment. */
export interface RuntimeConfig {
  API_BASE_URL: string;
}

export const CONFIG_URL = 'config.json';
/** Used until config.json answers, or when it is missing (relative: same origin / dev proxy). */
export const DEFAULT_API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  // HttpBackend skips interceptors: the config is loaded before any session exists.
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly baseUrl = signal(DEFAULT_API_BASE_URL);

  readonly apiBaseUrl = this.baseUrl.asReadonly();

  async load(): Promise<void> {
    try {
      const config = await firstValueFrom(this.http.get<Partial<RuntimeConfig>>(CONFIG_URL));
      const url = config?.API_BASE_URL?.trim();
      if (url) {
        this.baseUrl.set(url.replace(/\/+$/, ''));
      }
    } catch {
      // Keep the same-origin default; the status page surfaces connectivity problems.
      console.warn(`Runtime config not available, using ${DEFAULT_API_BASE_URL}`);
    }
  }

  /** Absolute URL of a contract path, e.g. apiUrl('/system/info'). */
  apiUrl(path: string): string {
    return `${this.baseUrl()}${path.startsWith('/') ? path : `/${path}`}`;
  }

  /** True when the URL targets this application's API (only those carry the token). */
  isApiUrl(url: string): boolean {
    const base = this.baseUrl();
    return url === base || url.startsWith(`${base}/`);
  }
}
