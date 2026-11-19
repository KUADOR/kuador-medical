import { FULL_ACCOUNT, FULL_CATALOG, SUPER_ADMIN } from '../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
  signIn,
} from '../../../testing/app-harness';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { problem } from '../../../testing/fixtures';

describe('PlatformPermissionsPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  it('shows the SUPER_ADMIN the full catalog with its scope', async () => {
    await signIn(app, SUPER_ADMIN);
    await app.harness.navigateByUrl('/platform/permissions');
    app.backend.expectOne('/api/v1/platform/permissions').flush(FULL_CATALOG);
    await app.settle();

    const rows = allByTestId(app.page(), 'permission-row').map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim()),
    );
    expect(rows).toHaveLength(5);
    expect(rows[0]).toEqual(['Ver roles y el catálogo de permisos', 'roles.read', 'Clínica']);
    expect(rows[4]).toEqual([
      'Ver el catálogo completo de permisos de la plataforma',
      'platform.permissions.read',
      'Plataforma',
    ]);
  });

  it('shows a Spanish message when the API rejects the request', async () => {
    await signIn(app, SUPER_ADMIN);
    await app.harness.navigateByUrl('/platform/permissions');
    const { body, init } = problem(403, 'FORBIDDEN');
    app.backend.expectOne('/api/v1/platform/permissions').flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'load-error')?.textContent?.trim()).toBe(ERROR_MESSAGES.FORBIDDEN);
  });

  it('is closed to clinic accounts', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl('/platform/permissions');
    expect(app.router.url).toBe('/forbidden');
  });
});
