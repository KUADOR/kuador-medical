import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { OWNER_ROLE, RECEPTION_ROLE, TENANT_CATALOG } from '../../../testing/fixtures';
import { PlatformService } from '../platform/platform.service';
import { RolesService } from './roles.service';

describe('RolesService and PlatformService', () => {
  let roles: RolesService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    roles = TestBed.inject(RolesService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists roles and the assignable catalog of the clinic in the token', async () => {
    const list = firstValueFrom(roles.list());
    const listRequest = backend.expectOne('/api/v1/roles');
    expect(listRequest.request.method).toBe('GET');
    expect(listRequest.request.params.keys()).toEqual([]);
    listRequest.flush([OWNER_ROLE]);
    expect(await list).toEqual([OWNER_ROLE]);

    const catalog = firstValueFrom(roles.assignablePermissions());
    backend.expectOne({ method: 'GET', url: '/api/v1/permissions' }).flush(TENANT_CATALOG);
    expect(await catalog).toEqual(TENANT_CATALOG);
  });

  it('creates, reads, updates and deletes a role by id', async () => {
    const created = firstValueFrom(
      roles.create({ name: 'Recepción', description: null, permissions: ['roles.read'] }),
    );
    const create = backend.expectOne({ method: 'POST', url: '/api/v1/roles' });
    expect(create.request.body).toEqual({
      name: 'Recepción',
      description: null,
      permissions: ['roles.read'],
    });
    expect(create.request.body).not.toHaveProperty('tenantId');
    create.flush(RECEPTION_ROLE);
    expect(await created).toEqual(RECEPTION_ROLE);

    const url = `/api/v1/roles/${RECEPTION_ROLE.id}`;
    const read = firstValueFrom(roles.get(RECEPTION_ROLE.id));
    backend.expectOne({ method: 'GET', url }).flush(RECEPTION_ROLE);
    expect(await read).toEqual(RECEPTION_ROLE);

    const updated = firstValueFrom(
      roles.update(RECEPTION_ROLE.id, {
        name: 'Recepción',
        description: null,
        permissions: [],
        version: 3,
      }),
    );
    const update = backend.expectOne({ method: 'PUT', url });
    expect(update.request.body.version).toBe(3);
    update.flush({ ...RECEPTION_ROLE, permissions: [], version: 4 });
    expect((await updated).version).toBe(4);

    const deleted = firstValueFrom(roles.delete(RECEPTION_ROLE.id));
    backend
      .expectOne({ method: 'DELETE', url })
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(await deleted).toBeNull();
  });

  it('encodes the role id in the path', () => {
    roles.get('a/b').subscribe();
    backend.expectOne('/api/v1/roles/a%2Fb').flush(OWNER_ROLE);
  });

  it('reads the full catalog from the platform endpoint', async () => {
    const platform = TestBed.inject(PlatformService);
    const result = firstValueFrom(platform.permissions());
    backend.expectOne({ method: 'GET', url: '/api/v1/platform/permissions' }).flush(TENANT_CATALOG);
    expect(await result).toEqual(TENANT_CATALOG);
  });
});
