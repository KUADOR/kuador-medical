import { TestBed } from '@angular/core/testing';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { NoticeService } from '../../core/notice/notice.service';
import {
  ACTIVE_RECEPTIONIST,
  FULL_ACCOUNT,
  INVITED_DOCTOR,
  NORTH_BRANCH,
  STAFF_ADMIN_ACCOUNT,
  STAFF_READER_ACCOUNT,
  SUPER_ADMIN,
  problem,
} from '../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  buttonByText,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../testing/app-harness';
import type { StaffUser } from '../../core/api/api-types';
import { expectMigratedScreen } from '../../../testing/migrated-screen';

function page(items: StaffUser[], pageNumber = 0, totalItems = items.length) {
  return { page: pageNumber, size: 20, totalItems, items };
}

describe('StaffUsersPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(
    account = STAFF_ADMIN_ACCOUNT,
    users = [INVITED_DOCTOR, ACTIVE_RECEPTIONIST],
  ) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/staff');
    app.backend.expectOne((request) => request.url === '/api/v1/staff-users').flush(page(users));
    await app.settle();
  }

  function rows(): HTMLElement[] {
    return allByTestId(app.page(), 'staff-row');
  }

  it('lists the staff with their roles per branch and status in Spanish', async () => {
    await open();

    expect(rows()).toHaveLength(2);
    const doctor = rows()[0];
    expect(doctor.querySelector('a')?.textContent?.trim()).toBe('Ana Pérez');
    expect(doctor.querySelector('a')?.getAttribute('href')).toBe(`/staff/${INVITED_DOCTOR.id}`);
    expect(doctor.textContent).toContain('Médico en Main branch · Médico en North branch');
    expect(byTestId(doctor, 'status')?.textContent?.trim()).toBe('Invitación pendiente');
    expect(byTestId(rows()[1], 'status')?.textContent?.trim()).toBe('Activo');
    expect(byTestId(app.page(), 'range')?.textContent).toContain('Mostrando 1–2 de 2');
  });

  it('uses only the design system (MEDK-44)', async () => {
    await open();
    expectMigratedScreen(app.page());

    buttonByText(app.page(), 'Limpiar').click();
    app.backend.expectOne((r) => r.url === '/api/v1/staff-users').flush(page([]));
    await app.settle();
    expectMigratedScreen(app.page());
  });

  it('offers to invite only with identity.users.manage and roles.read', async () => {
    await open();
    expect(byTestId(app.page(), 'new-staff-user')?.getAttribute('href')).toBe('/staff/new');
  });

  it('only shows the list to someone who can just read it', async () => {
    await open(STAFF_READER_ACCOUNT);
    expect(byTestId(app.page(), 'new-staff-user')).toBeNull();
    expect(rows()).toHaveLength(2);
  });

  it('searches by text, status and branch', async () => {
    await open();
    type(app.page(), '#staff-q', '  ana ');
    const status = app.page().querySelector('#staff-status') as HTMLSelectElement;
    status.value = 'INVITED';
    status.dispatchEvent(new Event('change'));
    const branch = app.page().querySelector('#staff-branch') as HTMLSelectElement;
    branch.value = NORTH_BRANCH.id;
    branch.dispatchEvent(new Event('change'));
    submit(app.page());

    const request = app.backend.expectOne((r) => r.url === '/api/v1/staff-users');
    expect(request.request.params.get('q')).toBe('ana');
    expect(request.request.params.get('status')).toBe('INVITED');
    expect(request.request.params.get('branchId')).toBe(NORTH_BRANCH.id);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([]));
    await app.settle();

    expect(byTestId(app.page(), 'empty')?.textContent).toContain('No hay personas');
  });

  it('pages through the staff', async () => {
    const many = Array.from({ length: 20 }, (_, index) => ({
      ...ACTIVE_RECEPTIONIST,
      id: `00000000-0000-0000-0000-${String(index).padStart(12, '0')}`,
    }));
    await signIn(app, STAFF_ADMIN_ACCOUNT);
    await app.harness.navigateByUrl('/staff');
    app.backend.expectOne((r) => r.url === '/api/v1/staff-users').flush(page(many, 0, 25));
    await app.settle();

    expect(byTestId(app.page(), 'range')?.textContent).toContain('Mostrando 1–20 de 25');
    expect(buttonByText(app.page(), 'Anterior').disabled).toBe(true);
    buttonByText(app.page(), 'Siguiente').click();

    const next = app.backend.expectOne((r) => r.url === '/api/v1/staff-users');
    expect(next.request.params.get('page')).toBe('1');
    next.flush(page(many.slice(0, 5), 1, 25));
    await app.settle();

    expect(byTestId(app.page(), 'range')?.textContent).toContain('Mostrando 21–25 de 25');
    expect(buttonByText(app.page(), 'Siguiente').disabled).toBe(true);
  });

  it('shows the notice left by the invitation form', async () => {
    TestBed.inject(NoticeService).show('Se registró a Ana Pérez.');
    await open();
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se registró a Ana Pérez.');
  });

  it('explains a 403 from the API in Spanish and lets the person retry', async () => {
    await signIn(app, STAFF_ADMIN_ACCOUNT);
    await app.harness.navigateByUrl('/staff');
    const { body, init } = problem(403, 'FORBIDDEN');
    app.backend.expectOne((r) => r.url === '/api/v1/staff-users').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(ERROR_MESSAGES.FORBIDDEN);
    buttonByText(app.page(), 'Volver a intentar').click();
    app.backend.expectOne((r) => r.url === '/api/v1/staff-users').flush(page([]));
    await app.settle();
    expect(byTestId(app.page(), 'empty')).not.toBeNull();
  });

  it('is closed without identity.users.read and to the SUPER_ADMIN', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/staff');
    expect(app.router.url).toBe('/forbidden');

    app.session.clear();
    await signIn(app, SUPER_ADMIN);
    await app.harness.navigateByUrl('/staff');
    expect(app.router.url).toBe('/forbidden');
  });

  it('is closed when the plan does not enable the identity module', async () => {
    await signIn(app, { ...STAFF_ADMIN_ACCOUNT, enabledModules: ['patients'] });
    await app.harness.navigateByUrl('/staff');
    expect(app.router.url).toBe('/forbidden');
  });
});
