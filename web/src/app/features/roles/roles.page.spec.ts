import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { NoticeService } from '../../core/notice/notice.service';
import {
  ADMIN_ROLE,
  DOCTOR_ROLE,
  FULL_ACCOUNT,
  STAFF_ADMIN_ACCOUNT,
  STAFF_OWNER_ACCOUNT,
  OWNER_ROLE,
  READ_ONLY_ACCOUNT,
  RECEPTION_ROLE,
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
} from '../../../testing/app-harness';
import { TestBed } from '@angular/core/testing';
import { expectMigratedScreen } from '../../../testing/migrated-screen';
import { sortRoles } from './roles.page';

describe('RolesPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(account = FULL_ACCOUNT, roles = [RECEPTION_ROLE, ADMIN_ROLE, OWNER_ROLE]) {
    await signIn(app, account);
    await app.harness.navigateByUrl('/roles');
    app.backend.expectOne('/api/v1/roles').flush(roles);
    await app.settle();
  }

  function actions(card: HTMLElement): string[] {
    return Array.from(card.querySelectorAll('.kd-card__actions a, .kd-card__actions button')).map(
      (element) => element.textContent?.trim().split(/\s+/)[0] ?? '',
    );
  }

  function roleCard(name: string): HTMLElement {
    const card = allByTestId(app.page(), 'role').find(
      (element) => element.querySelector('h2')?.textContent?.trim() === name,
    );
    if (!card) {
      throw new Error(`No role card ${name}`);
    }
    return card;
  }

  it('lists system roles first, as read-only, with Spanish permission labels', async () => {
    await open();

    const names = allByTestId(app.page(), 'role').map((c) =>
      c.querySelector('h2')?.textContent?.trim(),
    );
    expect(names).toEqual(['ADMIN', 'OWNER', 'Recepción']);

    const owner = roleCard('OWNER');
    expect(byTestId(owner, 'system-badge')?.textContent).toContain('solo lectura');
    expect(owner.textContent).toContain('Crear roles');
    expect(actions(owner)).toEqual(['Ver']);
    expect(actions(roleCard('Recepción'))).toEqual(['Editar', 'Eliminar']);
    expect(byTestId(app.page(), 'new-role')).not.toBeNull();
  });

  it('never offers to delete ADMIN and only lets an OWNER edit it', async () => {
    await open(STAFF_ADMIN_ACCOUNT);
    expect(byTestId(roleCard('ADMIN'), 'system-badge')?.textContent).toContain(
      'no se puede eliminar',
    );
    expect(actions(roleCard('ADMIN'))).toEqual(['Ver']);
    expect(actions(roleCard('OWNER'))).toEqual(['Ver']);
  });

  it('lets an OWNER edit ADMIN but never delete it, and OWNER stays read-only', async () => {
    await open(STAFF_OWNER_ACCOUNT, [OWNER_ROLE, ADMIN_ROLE, DOCTOR_ROLE]);
    expect(actions(roleCard('ADMIN'))).toEqual(['Editar']);
    expect(actions(roleCard('OWNER'))).toEqual(['Ver']);
    expect(byTestId(roleCard('Médico'), 'clinical-badge')?.textContent).toContain('Rol clínico');
    expect(byTestId(roleCard('ADMIN'), 'clinical-badge')).toBeNull();
  });

  it('hides create, edit and delete without those permissions', async () => {
    await open(READ_ONLY_ACCOUNT);

    expect(byTestId(app.page(), 'new-role')).toBeNull();
    expect(actions(roleCard('Recepción'))).toEqual(['Ver']);
  });

  it('asks for confirmation, deletes the role and reloads the list', async () => {
    await open();
    buttonByText(roleCard('Recepción'), 'Eliminar').click();
    await app.settle();

    const confirm = byTestId(app.page(), 'confirm-delete') as HTMLButtonElement;
    expect(app.page().querySelector('[role="alertdialog"]')?.textContent).toContain(
      '¿Eliminar el rol «Recepción»?',
    );
    expect(document.activeElement).toBe(confirm);
    confirm.click();

    app.backend
      .expectOne({ method: 'DELETE', url: `/api/v1/roles/${RECEPTION_ROLE.id}` })
      .flush(null, { status: 204, statusText: 'No Content' });
    app.backend.expectOne('/api/v1/roles').flush([OWNER_ROLE, ADMIN_ROLE]);
    await app.settle();

    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se eliminó el rol «Recepción».');
    expect(allByTestId(app.page(), 'role')).toHaveLength(2);
  });

  it('does not delete when the person cancels', async () => {
    await open();
    buttonByText(roleCard('Recepción'), 'Eliminar').click();
    await app.settle();
    buttonByText(app.page(), 'Cancelar').click();
    await app.settle();

    expect(app.page().querySelector('[role="alertdialog"]')).toBeNull();
    app.backend.expectNone(`/api/v1/roles/${RECEPTION_ROLE.id}`);
  });

  it('explains in Spanish why a role in use cannot be deleted', async () => {
    await open();
    buttonByText(roleCard('Recepción'), 'Eliminar').click();
    await app.settle();
    (byTestId(app.page(), 'confirm-delete') as HTMLButtonElement).click();
    const { body, init } = problem(409, 'ROLE_IN_USE');
    app.backend.expectOne(`/api/v1/roles/${RECEPTION_ROLE.id}`).flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'action-error')?.textContent?.trim()).toBe(
      ERROR_MESSAGES.ROLE_IN_USE,
    );
    expect(allByTestId(app.page(), 'role')).toHaveLength(3);
  });

  it('shows the notice left by the role form', async () => {
    TestBed.inject(NoticeService).show('Se creó el rol «Caja».');
    await open();
    expect(byTestId(app.page(), 'notice')?.textContent).toContain('Se creó el rol «Caja».');
  });

  it('shows a Spanish error and retries when the list fails', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/roles');
    app.backend.expectOne('/api/v1/roles').error(new ProgressEvent('error'));
    await app.settle();
    expect(byTestId(app.page(), 'load-error')?.textContent).toContain('No pudimos conectar');

    buttonByText(app.page(), 'Volver a intentar').click();
    app.backend.expectOne('/api/v1/roles').flush([OWNER_ROLE]);
    await app.settle();
    expect(allByTestId(app.page(), 'role')).toHaveLength(1);
  });

  it('uses only the design system (MEDK-44)', async () => {
    TestBed.inject(NoticeService).show('Se creó el rol «Caja».');
    await open();
    expectMigratedScreen(app.page());

    buttonByText(roleCard('Recepción'), 'Eliminar').click();
    await app.settle();
    expectMigratedScreen(app.page());
    expect(app.page().querySelector('[role="alertdialog"] .kd-button--danger')).not.toBeNull();
  });

  it('keeps the empty and failed states inside the design system (MEDK-44)', async () => {
    await open(FULL_ACCOUNT, []);
    expect(byTestId(app.page(), 'empty')?.textContent).toContain('todavía no tiene roles');
    expectMigratedScreen(app.page());
  });

  it('keeps the failed state inside the design system (MEDK-44)', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/roles');
    app.backend.expectOne('/api/v1/roles').error(new ProgressEvent('error'));
    await app.settle();

    expectMigratedScreen(app.page());
  });

  it('is closed to the SUPER_ADMIN, who has no clinic permissions', async () => {
    await signIn(app, SUPER_ADMIN);
    await app.harness.navigateByUrl('/roles');
    expect(app.router.url).toBe('/forbidden');
  });

  it('sorts system roles first and the rest alphabetically', () => {
    const caja = { ...RECEPTION_ROLE, id: 'x', name: 'caja' };
    expect(sortRoles([RECEPTION_ROLE, caja, OWNER_ROLE]).map((r) => r.name)).toEqual([
      'OWNER',
      'caja',
      'Recepción',
    ]);
  });
});
