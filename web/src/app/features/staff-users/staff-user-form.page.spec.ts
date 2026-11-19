import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import {
  ADMIN_ROLE,
  DOCTOR_ROLE,
  INVITED_DOCTOR,
  MAIN_BRANCH,
  NORTH_BRANCH,
  OWNER_ROLE,
  RECEPTION_ROLE,
  STAFF_ADMIN_ACCOUNT,
  STAFF_OWNER_ACCOUNT,
  STAFF_READER_ACCOUNT,
  problem,
} from '../../../testing/fixtures';
import {
  AppHarness,
  byTestId,
  createAppHarness,
  signIn,
  submit,
  type,
} from '../../../testing/app-harness';
import { ASSIGNMENT_REQUIRED_MESSAGE } from './staff-user-form.page';

const ROLES = [OWNER_ROLE, ADMIN_ROLE, RECEPTION_ROLE, DOCTOR_ROLE];

describe('StaffUserFormPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(account = STAFF_ADMIN_ACCOUNT) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/staff/new');
    app.backend.expectOne('/api/v1/roles').flush(ROLES);
    await app.settle();
  }

  function select(selector: string, value: string): void {
    const element = app.page().querySelector(selector) as HTMLSelectElement | null;
    if (!element) {
      throw new Error(`No select ${selector}`);
    }
    element.value = value;
    element.dispatchEvent(new Event('change'));
  }

  function options(selector: string): string[] {
    return Array.from(
      (app.page().querySelector(selector) as HTMLSelectElement).options,
      (option) => option.textContent?.trim() ?? '',
    );
  }

  async function fillPerson(registration = '') {
    type(app.page(), '#staff-first-names', ' Ana ');
    type(app.page(), '#staff-last-names', 'Pérez');
    type(app.page(), '#staff-email', 'ana.perez@example.com');
    type(app.page(), '#staff-job-title', 'Médica general');
    if (registration) {
      type(app.page(), '#staff-registration', registration);
    }
    await app.settle();
  }

  it('invites with the same role in every branch, a client id and an idempotency key', async () => {
    await open();
    await fillPerson();
    select('#staff-assignments-role', RECEPTION_ROLE.id);
    await app.settle();
    submit(app.page());

    const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' });
    const { id, ...body } = request.request.body;
    expect(body).toEqual({
      firstNames: 'Ana',
      lastNames: 'Pérez',
      email: 'ana.perez@example.com',
      jobTitle: 'Médica general',
      roleAssignments: { mode: 'ALL_BRANCHES', roleId: RECEPTION_ROLE.id },
    });
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush(INVITED_DOCTOR, { status: 201, statusText: 'Created' });
    await app.settle();

    expect(app.router.url).toBe('/staff');
    app.backend
      .expectOne((r) => r.url === '/api/v1/staff-users')
      .flush({
        page: 0,
        size: 20,
        totalItems: 0,
        items: [],
      });
    await app.settle();
    expect(byTestId(app.page(), 'notice')?.textContent).toContain(
      'Se registró a Ana Pérez y le enviamos la invitación a ana.perez@example.com.',
    );
  });

  it('assigns a different role per branch, with no access in some', async () => {
    await open();
    await fillPerson('REG-1');
    (byTestId(app.page(), 'mode-per-branch') as HTMLInputElement).click();
    await app.settle();
    select(`#staff-assignments-branch-${MAIN_BRANCH.id}`, ADMIN_ROLE.id);
    select(`#staff-assignments-branch-${NORTH_BRANCH.id}`, DOCTOR_ROLE.id);
    await app.settle();
    submit(app.page());

    const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' });
    expect(request.request.body.professionalRegistration).toBe('REG-1');
    expect(request.request.body.roleAssignments).toEqual({
      mode: 'PER_BRANCH',
      assignments: [
        { branchId: MAIN_BRANCH.id, roleId: ADMIN_ROLE.id },
        { branchId: NORTH_BRANCH.id, roleId: DOCTOR_ROLE.id },
      ],
    });
    request.flush(INVITED_DOCTOR, { status: 201, statusText: 'Created' });
    await app.settle();
    app.backend
      .expectOne((r) => r.url === '/api/v1/staff-users')
      .flush({
        page: 0,
        size: 20,
        totalItems: 0,
        items: [],
      });
  });

  it('requires a role before calling the API', async () => {
    await open();
    await fillPerson();
    submit(app.page());
    await app.settle();

    expect(byTestId(app.page(), 'assignments-editor')?.textContent).toContain(
      ASSIGNMENT_REQUIRED_MESSAGE,
    );
    expect(document.activeElement).toBe(app.page().querySelector('#staff-assignments-role'));
    app.backend.expectNone({ method: 'POST', url: '/api/v1/staff-users' });
  });

  it('requires the professional registration for a clinical role', async () => {
    await open();
    await fillPerson();
    expect(byTestId(app.page(), 'registration-hint')?.textContent).toContain(
      'Obligatorio solo para roles clínicos',
    );
    select('#staff-assignments-role', DOCTOR_ROLE.id);
    await app.settle();
    expect(byTestId(app.page(), 'registration-hint')?.textContent).toContain(
      'Obligatorio: elegiste un rol clínico',
    );
    expect(options('#staff-assignments-role')).toContain('Médico (rol clínico)');
    submit(app.page());
    await app.settle();

    expect(app.page().querySelector('#staff-registration-error')?.textContent).toContain(
      'Este campo es obligatorio.',
    );
    expect(document.activeElement).toBe(app.page().querySelector('#staff-registration'));
    app.backend.expectNone({ method: 'POST', url: '/api/v1/staff-users' });
  });

  it('does not offer the OWNER role to an ADMIN', async () => {
    await open();
    expect(options('#staff-assignments-role')).not.toContain('OWNER');
    expect(options('#staff-assignments-role')).toContain('ADMIN');
  });

  it('offers the OWNER role to an OWNER, only for every branch', async () => {
    await open(STAFF_OWNER_ACCOUNT);
    expect(options('#staff-assignments-role')).toContain('OWNER');
    (byTestId(app.page(), 'mode-per-branch') as HTMLInputElement).click();
    await app.settle();
    expect(options(`#staff-assignments-branch-${MAIN_BRANCH.id}`)).not.toContain('OWNER');
    expect(options(`#staff-assignments-branch-${MAIN_BRANCH.id}`)[0]).toBe('Sin acceso');
  });

  it('marks the email when it is already used in the clinic', async () => {
    await open();
    await fillPerson();
    select('#staff-assignments-role', RECEPTION_ROLE.id);
    submit(app.page());
    const { body, init } = problem(409, 'EMAIL_TAKEN');
    app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' }).flush(body, init);
    await app.settle();

    expect(app.page().querySelector('#staff-email')?.getAttribute('aria-invalid')).toBe('true');
    expect(app.page().querySelector('#staff-email-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.EMAIL_TAKEN,
    );
    expect(app.router.url).toBe('/staff/new');
  });

  it.each([
    [409, 'PLAN_LIMIT_REACHED'],
    [403, 'PRIVILEGE_ESCALATION'],
    [403, 'FORBIDDEN'],
  ] as const)('explains a %s %s in Spanish', async (status, code) => {
    await open();
    await fillPerson();
    select('#staff-assignments-role', RECEPTION_ROLE.id);
    submit(app.page());
    const { body, init } = problem(status, code);
    app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' }).flush(body, init);
    await app.settle();

    const alert = byTestId(app.page(), 'form-error');
    expect(alert?.textContent?.trim()).toBe(ERROR_MESSAGES[code]);
    expect(document.activeElement).toBe(alert);
  });

  it('retries with the same key after a network error, and a new one after a rejection', async () => {
    await open();
    await fillPerson();
    select('#staff-assignments-role', RECEPTION_ROLE.id);
    submit(app.page());
    const first = app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' });
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'));
    await app.settle();

    submit(app.page());
    const second = app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' });
    expect(second.request.headers.get('Idempotency-Key')).toBe(firstKey);
    expect(second.request.body.id).toBe(first.request.body.id);
    const { body, init } = problem(409, 'PLAN_LIMIT_REACHED');
    second.flush(body, init);
    await app.settle();

    submit(app.page());
    const third = app.backend.expectOne({ method: 'POST', url: '/api/v1/staff-users' });
    expect(third.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    third.flush(body, init);
    await app.settle();
  });

  it('is closed without identity.users.manage', async () => {
    await signIn(app, STAFF_READER_ACCOUNT);
    await app.harness.navigateByUrl('/staff/new');
    expect(app.router.url).toBe('/forbidden');
  });
});
