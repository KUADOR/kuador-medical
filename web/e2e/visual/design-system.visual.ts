// Design system library (MEDK-40): screenshots of the living catalog in light, dark and mobile,
// axe (with color contrast, real browser), visible keyboard focus and 44 px targets (R-05)
// on every control. Synthetic data only; the catalog route exists in development builds only.
import { expect, test } from '@playwright/test';
import { controlsWithoutFocusRing, expectTargetsOf44, targetSizes } from './support/a11y';
import { expectNoAxeViolations } from './support/axe';
import { simulateApi } from './support/simulated-api';

test.describe('design system catalog', () => {
  test('all components', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/design-system');
    await expect(page.getByRole('heading', { level: 1, name: 'Sistema de diseño' })).toBeVisible();

    await expect(page).toHaveScreenshot('catalog.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('every control is at least 44 × 44 px (R-05)', async ({ page }) => {
    await simulateApi(page);
    await page.goto('/design-system');

    expectTargetsOf44(await targetSizes(page.locator('.catalog')));
  });

  test('every control shows the focus ring with the keyboard (R-09)', async ({ page }) => {
    await simulateApi(page);
    await page.goto('/design-system');

    expect(await controlsWithoutFocusRing(page, '.catalog')).toEqual([]);
  });

  test('form errors after submit', async ({ page }) => {
    await simulateApi(page);
    await page.goto('/design-system');
    await page.getByRole('button', { name: 'Guardar paciente' }).click();

    const email = page.getByLabel('Correo', { exact: true });
    await expect(email).toHaveAttribute('aria-invalid', 'true');
    await expect(email).toHaveAccessibleDescription('Este campo es obligatorio.');
    await expect(
      page.getByRole('alert').filter({ hasText: 'Revisa los datos marcados' }),
    ).toBeVisible();

    await page.getByRole('link', { name: 'Escribe el correo del paciente.' }).click();
    await expect(email).toBeFocused();

    await expect(page.locator('form')).toHaveScreenshot('form-errors.png');
    await expectNoAxeViolations(page);
  });

  test('dialog', async ({ page }) => {
    await simulateApi(page);
    await page.goto('/design-system');
    const opener = page.getByRole('button', { name: 'Cancelar cita' }).last();
    await opener.click();

    const dialog = page.getByRole('dialog', { name: '¿Cancelar la cita?' });
    await expect(dialog).toBeVisible();
    await expect(page).toHaveScreenshot('dialog.png');
    await expectNoAxeViolations(page);
    expectTargetsOf44(await targetSizes(dialog));

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(opener).toBeFocused();
  });
});

test.describe('design system shell', () => {
  test('responsive shell and navigation', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/design-system/shell');
    const nav = page.getByRole('navigation', { name: 'Navegación de ejemplo' });
    const menu = page.getByRole('button', { name: 'Abrir menú' });

    await expect(page.getByRole('link', { name: 'Clínica Ficticia' })).toBeVisible();

    // Below 64rem the navigation is behind the menu button; above, it is a side column.
    if ((page.viewportSize()?.width ?? 0) < 1024) {
      await expect(nav).toBeHidden();
      await menu.click();
      await expect(page.getByRole('button', { name: 'Cerrar menú' })).toHaveAttribute(
        'aria-expanded',
        'true',
      );
    } else {
      await expect(menu).toBeHidden();
    }
    await expect(nav).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Agenda' })).toHaveAttribute('aria-current', 'page');

    await expect(page).toHaveScreenshot('shell.png');
    await expectNoAxeViolations(page);
    expectTargetsOf44(await targetSizes(page.locator('kd-shell')));
    expect(await controlsWithoutFocusRing(page, 'kd-shell')).toEqual([]);
    api.expectNoUnexpectedCalls();
  });
});
