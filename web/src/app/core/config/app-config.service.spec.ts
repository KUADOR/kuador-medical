import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AppConfigService, CONFIG_URL, DEFAULT_API_BASE_URL } from './app-config.service';

describe('AppConfigService', () => {
  let service: AppConfigService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AppConfigService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads API_BASE_URL from config.json and trims trailing slashes', async () => {
    const loading = service.load();
    http.expectOne(CONFIG_URL).flush({ API_BASE_URL: 'https://api.example.test/api/v1/' });
    await loading;

    expect(service.apiBaseUrl()).toBe('https://api.example.test/api/v1');
    expect(service.apiUrl('/system/info')).toBe('https://api.example.test/api/v1/system/info');
  });

  it('keeps the same-origin default when config.json is missing', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const loading = service.load();
    http.expectOne(CONFIG_URL).flush('not found', { status: 404, statusText: 'Not Found' });
    await loading;

    expect(service.apiBaseUrl()).toBe(DEFAULT_API_BASE_URL);
    expect(warn).toHaveBeenCalled();
  });

  it('recognizes only URLs under the API base', () => {
    expect(service.isApiUrl('/api/v1/roles')).toBe(true);
    expect(service.isApiUrl('/api/v1')).toBe(true);
    expect(service.isApiUrl('/api/v10/roles')).toBe(false);
    expect(service.isApiUrl('https://third-party.test/api/v1/roles')).toBe(false);
  });
});
