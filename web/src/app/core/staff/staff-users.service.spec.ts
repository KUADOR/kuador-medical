import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ACTIVE_RECEPTIONIST, INVITED_DOCTOR } from '../../../testing/fixtures';
import { StaffUsersService, newCreationKey } from './staff-users.service';

describe('StaffUsersService', () => {
  let staff: StaffUsersService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    staff = TestBed.inject(StaffUsersService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists with only the filters given, never a tenant id', async () => {
    const page = firstValueFrom(
      staff.list({ q: 'ana', status: 'INVITED', branchId: undefined, page: 1, size: 20 }),
    );
    const request = backend.expectOne((r) => r.url === '/api/v1/staff-users');
    expect(request.request.params.keys().sort()).toEqual(['page', 'q', 'size', 'status']);
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ page: 1, size: 20, totalItems: 0, items: [] });
    expect((await page).items).toEqual([]);
  });

  it('creates with the client id and the idempotency key', async () => {
    const key = newCreationKey();
    const created = firstValueFrom(
      staff.create(
        {
          firstNames: 'Ana',
          lastNames: 'Pérez',
          email: 'ana.perez@example.com',
          jobTitle: 'Médica general',
          roleAssignments: { mode: 'ALL_BRANCHES', roleId: 'role-id' },
        },
        key,
      ),
    );
    const request = backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' });
    expect(request.request.headers.get('Idempotency-Key')).toBe(key.idempotencyKey);
    expect(request.request.body.id).toBe(key.id);
    expect(request.request.body).not.toHaveProperty('tenantId');
    request.flush(INVITED_DOCTOR, { status: 201, statusText: 'Created' });
    expect(await created).toEqual(INVITED_DOCTOR);
  });

  it('calls each action on the path of the user', async () => {
    const id = ACTIVE_RECEPTIONIST.id;
    const calls: [Promise<unknown>, string, string][] = [
      [firstValueFrom(staff.get(id)), 'GET', `/api/v1/staff-users/${id}`],
      [
        firstValueFrom(staff.update(id, { jobTitle: 'Jefa de recepción', version: 5 })),
        'PATCH',
        `/api/v1/staff-users/${id}`,
      ],
      [firstValueFrom(staff.disable(id)), 'POST', `/api/v1/staff-users/${id}/disable`],
      [firstValueFrom(staff.enable(id)), 'POST', `/api/v1/staff-users/${id}/enable`],
      [
        firstValueFrom(staff.replaceAssignments(id, { mode: 'ALL_BRANCHES', roleId: 'r' })),
        'PUT',
        `/api/v1/staff-users/${id}/role-assignments`,
      ],
    ];
    for (const [, method, url] of calls) {
      backend.expectOne({ method, url }).flush(method === 'PUT' ? [] : ACTIVE_RECEPTIONIST);
    }
    await Promise.all(calls.map(([call]) => call));

    const resent = firstValueFrom(staff.resendInvitation(id), { defaultValue: undefined });
    backend
      .expectOne({ method: 'POST', url: `/api/v1/staff-users/${id}/invitation` })
      .flush(null, { status: 202, statusText: 'Accepted' });
    await resent;
  });

  it('makes a different key for each new creation', () => {
    const first = newCreationKey();
    const second = newCreationKey();
    expect(first.id).not.toBe(second.id);
    expect(first.idempotencyKey).not.toBe(first.id);
  });
});
