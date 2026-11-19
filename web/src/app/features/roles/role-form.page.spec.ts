import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import {
  FULL_ACCOUNT,
  OWNER_ROLE,
  READ_ONLY_ACCOUNT,
  RECEPTION_ROLE,
  TENANT_CATALOG,
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

describe('RoleFormPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  function checkboxes(): HTMLInputElement[] {
    return allByTestId(app.page(), 'permission') as HTMLInputElement[];
  }

  function check(code: string): void {
    const box = checkboxes().find((input) => input.value === code);
    if (!box) {
      throw new Error(`No checkbox ${code}`);
    }
    box.click();
  }

  async function openNew(account = FULL_ACCOUNT) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/roles/new');
    app.backend.expectOne('/api/v1/permissions').flush(TENANT_CATALOG);
    await app.settle();
  }

  async function openExisting(role = RECEPTION_ROLE, account = FULL_ACCOUNT) {
    await signIn(app, account);
    await app.harness.navigateByUrl(`/roles/${role.id}`);
    app.backend.expectOne('/api/v1/permissions').flush(TENANT_CATALOG);
    app.backend.expectOne(`/api/v1/roles/${role.id}`).flush(role);
    await app.settle();
  }

  describe('create', () => {
    it('offers the clinic catalog with Spanish labels', async () => {
      await openNew();

      expect(app.page().querySelector('h1')?.textContent).toContain('Nuevo rol');
      expect(checkboxes().map((input) => input.value)).toEqual([
        'roles.read',
        'roles.create',
        'roles.update',
        'roles.delete',
      ]);
      expect(app.page().textContent).toContain('Ver roles y el catálogo de permisos');
      expect(app.page().textContent).not.toContain('platform.permissions.read');
    });

    it('requires a name before calling the API', async () => {
      await openNew();
      type(app.page(), '#role-name', '   ');
      submit(app.page());
      await app.settle();

      expect(app.page().querySelector('#role-name-error')?.textContent).toContain(
        'solo con espacios',
      );
      expect(document.activeElement).toBe(app.page().querySelector('#role-name'));
      app.backend.expectNone({ method: 'POST', url: '/api/v1/roles' });
    });

    it('creates the role with the chosen permissions and goes back to the list', async () => {
      await openNew();
      type(app.page(), '#role-name', ' Caja ');
      type(app.page(), '#role-description', 'Cobros en recepción');
      check('roles.update');
      check('roles.read');
      await app.settle();
      submit(app.page());

      const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/roles' });
      expect(request.request.body).toEqual({
        name: 'Caja',
        description: 'Cobros en recepción',
        permissions: ['roles.read', 'roles.update'],
      });
      request.flush({ ...RECEPTION_ROLE, name: 'Caja' }, { status: 201, statusText: 'Created' });
      await app.settle();

      expect(app.router.url).toBe('/roles');
      app.backend.expectOne('/api/v1/roles').flush([]);
      await app.settle();
      expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se creó el rol «Caja».');
    });

    it('sends a null description when it is left empty', async () => {
      await openNew();
      type(app.page(), '#role-name', 'Caja');
      submit(app.page());

      const request = app.backend.expectOne({ method: 'POST', url: '/api/v1/roles' });
      expect(request.request.body).toEqual({ name: 'Caja', description: null, permissions: [] });
      request.flush(RECEPTION_ROLE, { status: 201, statusText: 'Created' });
      await app.settle();
      app.backend.expectOne('/api/v1/roles').flush([]);
    });

    it('marks the name when it is already used in the clinic', async () => {
      await openNew();
      type(app.page(), '#role-name', 'OWNER');
      submit(app.page());
      const { body, init } = problem(409, 'ROLE_NAME_TAKEN');
      app.backend.expectOne({ method: 'POST', url: '/api/v1/roles' }).flush(body, init);
      await app.settle();

      expect(app.page().querySelector('#role-name')?.getAttribute('aria-invalid')).toBe('true');
      expect(app.page().querySelector('#role-name-error')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.ROLE_NAME_TAKEN,
      );
      expect(app.router.url).toBe('/roles/new');
    });

    it('shows in Spanish that a permission is not in the catalog', async () => {
      await openNew();
      type(app.page(), '#role-name', 'Caja');
      check('roles.read');
      submit(app.page());
      const { body, init } = problem(400, 'VALIDATION', [
        { field: 'permissions[0]', code: 'UNKNOWN_PERMISSION' },
      ]);
      app.backend.expectOne({ method: 'POST', url: '/api/v1/roles' }).flush(body, init);
      await app.settle();

      expect(app.page().querySelector('#role-permissions-error')?.textContent?.trim()).toBe(
        'El permiso no existe en el catálogo del sistema.',
      );
    });

    it('is closed without roles.create', async () => {
      await signIn(app, READ_ONLY_ACCOUNT);
      await app.harness.navigateByUrl('/roles/new');
      expect(app.router.url).toBe('/forbidden');
    });
  });

  describe('edit', () => {
    it('loads the role and saves it with its version', async () => {
      await openExisting();

      expect(app.page().querySelector('h1')?.textContent).toContain('Editar rol Recepción');
      expect((app.page().querySelector('#role-name') as HTMLInputElement).value).toBe('Recepción');
      expect(
        checkboxes()
          .filter((input) => input.checked)
          .map((input) => input.value),
      ).toEqual(['roles.read']);

      check('roles.read');
      check('roles.create');
      await app.settle();
      submit(app.page());

      const request = app.backend.expectOne({
        method: 'PUT',
        url: `/api/v1/roles/${RECEPTION_ROLE.id}`,
      });
      expect(request.request.body).toEqual({
        name: 'Recepción',
        description: 'Personal de recepción.',
        permissions: ['roles.create'],
        version: 3,
      });
      request.flush({ ...RECEPTION_ROLE, permissions: ['roles.create'], version: 4 });
      await app.settle();

      expect(app.router.url).toBe('/roles');
      app.backend.expectOne('/api/v1/roles').flush([]);
    });

    it('offers to reload when someone else changed the role', async () => {
      await openExisting();
      submit(app.page());
      const { body, init } = problem(409, 'VERSION_CONFLICT');
      app.backend.expectOne(`/api/v1/roles/${RECEPTION_ROLE.id}`).flush(body, init);
      await app.settle();

      expect(byTestId(app.page(), 'form-error')?.textContent).toContain(
        ERROR_MESSAGES.VERSION_CONFLICT,
      );
      buttonByText(app.page(), 'Recargar el rol').click();
      app.backend.expectOne('/api/v1/permissions').flush(TENANT_CATALOG);
      app.backend
        .expectOne(`/api/v1/roles/${RECEPTION_ROLE.id}`)
        .flush({ ...RECEPTION_ROLE, name: 'Recepción y caja', version: 4 });
      await app.settle();

      expect((app.page().querySelector('#role-name') as HTMLInputElement).value).toBe(
        'Recepción y caja',
      );
      expect(byTestId(app.page(), 'form-error')).toBeNull();
    });

    it('shows OWNER and ADMIN read-only, without a save button', async () => {
      await openExisting(OWNER_ROLE);

      expect(byTestId(app.page(), 'read-only')?.textContent).toContain(
        'OWNER y ADMIN son roles del sistema',
      );
      expect((app.page().querySelector('#role-name') as HTMLInputElement).disabled).toBe(true);
      expect(checkboxes().every((input) => input.disabled && input.checked)).toBe(true);
      expect(app.page().querySelector('button[type="submit"]')).toBeNull();
      submit(app.page());
      app.backend.expectNone(`/api/v1/roles/${OWNER_ROLE.id}`);
    });

    it('shows any role read-only without roles.update', async () => {
      await openExisting(RECEPTION_ROLE, READ_ONLY_ACCOUNT);

      expect(byTestId(app.page(), 'read-only')?.textContent).toContain(
        'No tienes permiso para editar roles',
      );
      expect(app.page().querySelector('button[type="submit"]')).toBeNull();
    });

    it('says so when the role does not exist in the clinic', async () => {
      await signIn(app, FULL_ACCOUNT);
      await app.harness.navigateByUrl('/roles/00000000-0000-0000-0000-00000000dead');
      app.backend.expectOne('/api/v1/permissions').flush(TENANT_CATALOG);
      const { body, init } = problem(404, 'NOT_FOUND');
      app.backend.expectOne('/api/v1/roles/00000000-0000-0000-0000-00000000dead').flush(body, init);
      await app.settle();

      expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(
        ERROR_MESSAGES.NOT_FOUND,
      );
      expect(app.page().querySelector('form')).toBeNull();
    });
  });
});
