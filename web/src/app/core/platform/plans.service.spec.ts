import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  DEFAULT_SETTINGS,
  LIMIT_CATALOG,
  MODULE_CATALOG,
  MONTHLY_PRICE,
  STANDARD_PLAN,
} from '../../../testing/fixtures';
import { PlansService, PlatformSettingsService } from './plans.service';

describe('PlansService and PlatformSettingsService', () => {
  let plans: PlansService;
  let settings: PlatformSettingsService;
  let backend: HttpTestingController;
  const key = { id: 'client-id', idempotencyKey: 'idem-key' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    plans = TestBed.inject(PlansService);
    settings = TestBed.inject(PlatformSettingsService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists every plan, or only those of one status, without any clinic id', async () => {
    const all = firstValueFrom(plans.list());
    const allRequest = backend.expectOne('/api/v1/platform/plans');
    expect(allRequest.request.method).toBe('GET');
    expect(allRequest.request.params.keys()).toEqual([]);
    allRequest.flush([STANDARD_PLAN]);
    expect(await all).toEqual([STANDARD_PLAN]);

    const archived = firstValueFrom(plans.list('ARCHIVED'));
    const filtered = backend.expectOne((r) => r.url === '/api/v1/platform/plans');
    expect(filtered.request.params.get('status')).toBe('ARCHIVED');
    filtered.flush([]);
    expect(await archived).toEqual([]);
  });

  it('creates a plan with a client id and an Idempotency-Key', async () => {
    const created = firstValueFrom(
      plans.create(
        {
          code: 'STANDARD',
          name: 'Estándar',
          trial: false,
          modules: ['AGENDA'],
          limits: { MAX_BRANCHES: 3 },
          prices: [{ billingPeriod: 'MONTHLY', currency: 'USD', amount: '49.00' }],
        },
        key,
      ),
    );
    const request = backend.expectOne({ method: 'POST', url: '/api/v1/platform/plans' });
    expect(request.request.headers.get('Idempotency-Key')).toBe('idem-key');
    expect(request.request.body).toEqual({
      id: 'client-id',
      code: 'STANDARD',
      name: 'Estándar',
      trial: false,
      modules: ['AGENDA'],
      limits: { MAX_BRANCHES: 3 },
      prices: [{ billingPeriod: 'MONTHLY', currency: 'USD', amount: '49.00' }],
    });
    request.flush(STANDARD_PLAN);
    expect(await created).toEqual(STANDARD_PLAN);
  });

  it('reads, updates and archives a plan by id', async () => {
    const id = STANDARD_PLAN.id;
    const read = firstValueFrom(plans.get(id));
    backend.expectOne({ method: 'GET', url: `/api/v1/platform/plans/${id}` }).flush(STANDARD_PLAN);
    expect(await read).toEqual(STANDARD_PLAN);

    const body = {
      name: 'Estándar',
      description: null,
      modules: ['AGENDA' as const],
      limits: { MAX_BRANCHES: 3 },
      version: 3,
    };
    const updated = firstValueFrom(plans.update(id, body));
    const put = backend.expectOne({ method: 'PUT', url: `/api/v1/platform/plans/${id}` });
    expect(put.request.body).toEqual(body);
    put.flush(STANDARD_PLAN);
    await updated;

    const archived = firstValueFrom(plans.archive(id));
    backend
      .expectOne({ method: 'POST', url: `/api/v1/platform/plans/${id}/archive` })
      .flush({ ...STANDARD_PLAN, status: 'ARCHIVED' });
    expect((await archived).status).toBe('ARCHIVED');
  });

  it('lists and publishes immutable price versions', async () => {
    const id = STANDARD_PLAN.id;
    const history = firstValueFrom(plans.prices(id, 'MONTHLY'));
    const list = backend.expectOne((r) => r.url === `/api/v1/platform/plans/${id}/prices`);
    expect(list.request.params.get('billingPeriod')).toBe('MONTHLY');
    list.flush([MONTHLY_PRICE]);
    expect(await history).toEqual([MONTHLY_PRICE]);

    const published = firstValueFrom(
      plans.createPrice(
        id,
        {
          billingPeriod: 'MONTHLY',
          currency: 'USD',
          amount: '55.00',
          effectiveFrom: '2030-03-01T05:00:00Z',
        },
        key,
      ),
    );
    const post = backend.expectOne({ method: 'POST', url: `/api/v1/platform/plans/${id}/prices` });
    expect(post.request.headers.get('Idempotency-Key')).toBe('idem-key');
    expect(post.request.body).toEqual({
      id: 'client-id',
      billingPeriod: 'MONTHLY',
      currency: 'USD',
      amount: '55.00',
      effectiveFrom: '2030-03-01T05:00:00Z',
    });
    post.flush(MONTHLY_PRICE);
    await published;
  });

  it('reads the module and limit catalogs', async () => {
    const modules = firstValueFrom(plans.modules());
    backend.expectOne('/api/v1/platform/catalog/modules').flush(MODULE_CATALOG);
    expect(await modules).toEqual(MODULE_CATALOG);

    const limits = firstValueFrom(plans.limits());
    backend.expectOne('/api/v1/platform/catalog/limits').flush(LIMIT_CATALOG);
    expect(await limits).toEqual(LIMIT_CATALOG);
  });

  it('reads and replaces the platform settings with their version', async () => {
    const read = firstValueFrom(settings.get());
    backend.expectOne({ method: 'GET', url: '/api/v1/platform/settings' }).flush(DEFAULT_SETTINGS);
    expect(await read).toEqual(DEFAULT_SETTINGS);

    const changed = { ...DEFAULT_SETTINGS, graceDays: 5 };
    const saved = firstValueFrom(settings.update(changed));
    const put = backend.expectOne({ method: 'PUT', url: '/api/v1/platform/settings' });
    expect(put.request.body).toEqual(changed);
    put.flush({ ...changed, version: 2 });
    expect((await saved).version).toBe(2);
  });
});
