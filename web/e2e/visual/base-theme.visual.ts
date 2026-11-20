// Base theme (MEDK-37): tokens, light and dark themes, visible focus and accessibility of the
// current pages, against a simulated API with synthetic data.
import { type Browser, expect, test } from '@playwright/test';
import { expectNoAxeViolations } from './support/axe';
import { SYNTHETIC, simulateApi } from './support/simulated-api';

test.describe('base theme', () => {
  test('login page', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/login');
    await expect(page.getByRole('heading', { level: 1, name: 'Ingresar' })).toBeVisible();

    await expect(page).toHaveScreenshot('login.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('login page with validation errors and keyboard focus', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/login');
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page.getByText('Este campo es obligatorio.').first()).toBeVisible();
    await page.getByLabel('Correo').focus();
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');

    await expect(page).toHaveScreenshot('login-errors-focus.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('system status page', async ({ page }) => {
    const api = await simulateApi(page, { 'GET /system/info': { body: SYNTHETIC.systemInfoUp } });
    await page.goto('/status');
    await expect(page.getByText('0.0.0-visual')).toBeVisible();

    await expect(page).toHaveScreenshot('status.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('system status page with the database down', async ({ page }) => {
    const api = await simulateApi(page, {
      'GET /system/info': { body: SYNTHETIC.systemInfoDegraded },
    });
    await page.goto('/status');
    await expect(page.getByText('No disponible')).toBeVisible();

    await expect(page).toHaveScreenshot('status-degraded.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });
});

test.describe('theme selection', () => {
  async function surfaceBase(
    browser: Browser,
    colorScheme: 'light' | 'dark',
    forcedTheme?: 'light' | 'dark',
  ): Promise<string> {
    const context = await browser.newContext({ colorScheme });
    const page = await context.newPage();
    await simulateApi(page);
    await page.goto('/login');
    if (forcedTheme) {
      await page.evaluate((t) => document.documentElement.setAttribute('data-theme', t), forcedTheme);
    }
    // The token, not the painted color: with reduced motion every property change is a
    // near-instant transition, so the computed background may still be the previous one.
    const color = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--kd-surface-base').trim(),
    );
    await context.close();
    return color;
  }

  test('follows the system color scheme and can be forced with data-theme', async ({ browser }) => {
    const light = await surfaceBase(browser, 'light');
    const dark = await surfaceBase(browser, 'dark');

    expect(light).toMatch(/^#/);
    expect(dark).toMatch(/^#/);
    expect(dark).not.toBe(light);
    expect(await surfaceBase(browser, 'dark', 'light')).toBe(light);
    expect(await surfaceBase(browser, 'light', 'dark')).toBe(dark);
  });

  test('cuts transitions when the user prefers reduced motion', async ({ page }) => {
    await simulateApi(page);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/login');
    const duration = await page.evaluate(() => {
      const probe = document.createElement('div');
      probe.style.transition = 'opacity 2s';
      document.body.appendChild(probe);
      return getComputedStyle(probe).transitionDuration;
    });

    expect(parseFloat(duration)).toBeLessThan(0.001);
  });
});
