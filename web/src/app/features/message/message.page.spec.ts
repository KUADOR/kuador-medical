import { AppHarness, createAppHarness } from '../../../testing/app-harness';
import { expectMigratedScreen } from '../../../testing/migrated-screen';

describe('MessagePage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  it.each([
    ['/forbidden', 'Sin permiso', 'No tienes permiso para ver esta página.'],
    ['/no-such-page', 'Página no encontrada', 'La página que buscas no existe.'],
  ])('shows the message of %s from the route data', async (url, heading, message) => {
    await app.harness.navigateByUrl(url);
    await app.settle();
    const page = app.page();

    expect(page.querySelector('h1')?.textContent?.trim()).toBe(heading);
    expect(page.textContent).toContain(message);
    expect(page.querySelector('a[href="/"]')?.textContent?.trim()).toBe('Ir al estado del sistema');
  });

  it('uses only the design system (MEDK-44)', async () => {
    await app.harness.navigateByUrl('/forbidden');
    await app.settle();

    expectMigratedScreen(app.page());
  });
});
