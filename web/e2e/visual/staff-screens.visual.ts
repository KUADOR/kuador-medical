// Main staff screens redesigned in MEDK-42: login (with the clinic selection), patient search
// and the navigation. Screenshots in light, dark and mobile, axe with color contrast, 44 px
// targets, keyboard focus, navigation filtered by permission and plan (risk R9) and the number
// of actions of each flow against the Phase 0 baseline (audit.md § 3: F1 ≤ 3, F2 ≤ 3, F4 ≤ 1).
// Synthetic data only, against the simulated API.
import { type Page, expect, test } from '@playwright/test';
import type { CurrentSession } from '../../src/app/core/api/api-types';
import { controlsWithoutFocusRing, expectTargetsOf44, targetSizes } from './support/a11y';
import { expectNoAxeViolations } from './support/axe';
import { SYNTHETIC, type SimulatedRoutes, simulateApi } from './support/simulated-api';

const PASSWORD = 'synthetic-passphrase';

/** Counts the interactive steps of a flow: a field filled, a click or a submit. */
class FlowCounter {
  count = 0;

  constructor(private readonly page: Page) {}

  async fill(label: string, value: string): Promise<void> {
    await this.page.getByLabel(label, { exact: true }).fill(value);
    this.count++;
  }

  async click(role: 'button' | 'link', name: string): Promise<void> {
    await this.page.getByRole(role, { name, exact: true }).click();
    this.count++;
  }
}

function staffRoutes(account: CurrentSession, extra: SimulatedRoutes = {}): SimulatedRoutes {
  return {
    'POST /auth/login': { body: SYNTHETIC.login },
    'GET /me': { body: account },
    'GET /patients': { body: SYNTHETIC.patients },
    ...extra,
  };
}

/** Logs in through the form; returns the number of actions it took (F1). */
async function logIn(page: Page): Promise<number> {
  const flow = new FlowCounter(page);
  await page.goto('/login');
  await flow.fill('Correo', 'persona@clinica-ficticia.example.test');
  await flow.fill('Contraseña', PASSWORD);
  await flow.click('button', 'Ingresar');
  return flow.count;
}

/** Moves the pointer off the content so no row is captured in its hover state. */
async function restPointer(page: Page): Promise<void> {
  await page.mouse.move(0, 0);
}

function isMobile(page: Page): boolean {
  return (page.viewportSize()?.width ?? 0) < 1024;
}

/** Labels of the main navigation; on small screens it opens the menu first. */
async function navLabels(page: Page): Promise<string[]> {
  if (isMobile(page)) {
    await page.getByRole('button', { name: 'Abrir menú' }).click();
  }
  const nav = page.getByRole('navigation', { name: 'Navegación principal' });
  await expect(nav).toBeVisible();
  return (await nav.getByRole('link').allTextContents()).map((text) => text.trim());
}

test.describe('login', () => {
  test('clinic selection', async ({ page }) => {
    const api = await simulateApi(page, {
      'POST /auth/login': { body: SYNTHETIC.clinicSelection },
    });
    await logIn(page);
    await expect(page.getByRole('heading', { level: 1, name: 'Elige la clínica' })).toBeVisible();

    await restPointer(page);
    await expect(page).toHaveScreenshot('login-clinic.png', { fullPage: true });
    await expectNoAxeViolations(page);
    expectTargetsOf44(await targetSizes(page.locator('main')));
    api.expectNoUnexpectedCalls();
  });

  test('every control of the login is at least 44 px and shows the focus ring', async ({
    page,
  }) => {
    await simulateApi(page);
    await page.goto('/login');
    await expect(page.getByRole('heading', { level: 1, name: 'Ingresar' })).toBeVisible();

    expectTargetsOf44(await targetSizes(page.locator('main')));
    expect(await controlsWithoutFocusRing(page, 'kd-shell')).toEqual([]);
    await expect(page.locator('.kd-button--primary')).toHaveCount(1);
  });
});

test.describe('patient search', () => {
  test('staff land on the search after the login, within the baseline (F1, F4)', async ({
    page,
  }) => {
    const api = await simulateApi(page, staffRoutes(SYNTHETIC.staffAccount));
    const actions = await logIn(page);

    await expect(page).toHaveURL('/patients');
    await expect(page.getByLabel('Buscar por nombre o número de documento')).toBeVisible();
    expect(actions, 'F1: actions to log in').toBeLessThanOrEqual(3);
    // F4 (reach the search after the login) needs no extra action: it is the landing page.
    api.expectNoUnexpectedCalls();
  });

  test('results', async ({ page }) => {
    const api = await simulateApi(page, staffRoutes(SYNTHETIC.staffAccount));
    await logIn(page);
    await expect(page.getByText('3 pacientes')).toBeVisible();

    await restPointer(page);
    await expect(page).toHaveScreenshot('patients.png', { fullPage: true });
    await expectNoAxeViolations(page);
    expectTargetsOf44(await targetSizes(page.locator('main')));
    expect(await controlsWithoutFocusRing(page, 'kd-shell')).toEqual([]);
    await expect(page.locator('main .kd-button--primary')).toHaveCount(1);
    api.expectNoUnexpectedCalls();
  });

  test('empty search', async ({ page }) => {
    const api = await simulateApi(
      page,
      staffRoutes(SYNTHETIC.staffAccount, { 'GET /patients': { body: SYNTHETIC.noPatients } }),
    );
    await logIn(page);
    await page.getByLabel('Buscar por nombre o número de documento').fill('Nadie');
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sin resultados' })).toBeVisible();

    await restPointer(page);
    await expect(page).toHaveScreenshot('patients-empty.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('error', async ({ page }) => {
    const api = await simulateApi(
      page,
      staffRoutes(SYNTHETIC.staffAccount, {
        'GET /patients': { status: 500, body: { status: 500, code: 'INTERNAL_ERROR' } },
      }),
    );
    await logIn(page);
    await expect(
      page.getByRole('heading', { name: 'No pudimos cargar los pacientes' }),
    ).toBeVisible();

    await restPointer(page);
    await expect(page).toHaveScreenshot('patients-error.png', { fullPage: true });
    await expectNoAxeViolations(page);
    api.expectNoUnexpectedCalls();
  });

  test('find a patient and open the record within the baseline (F2)', async ({ page }) => {
    await simulateApi(
      page,
      staffRoutes(SYNTHETIC.staffAccount, {
        [`GET /patients/${SYNTHETIC.patient.id}`]: { body: SYNTHETIC.patient },
        [`GET /patients/${SYNTHETIC.patient.id}/guardian-links`]: { body: [] },
      }),
    );
    await logIn(page);
    await expect(page.getByText('3 pacientes')).toBeVisible();

    const flow = new FlowCounter(page);
    await flow.fill('Buscar por nombre o número de documento', 'Ficticio Uno');
    await flow.click('button', 'Buscar');
    await page.getByRole('link', { name: /Paciente Ficticio Uno/ }).click();
    flow.count++;

    await expect(page).toHaveURL(`/patients/${SYNTHETIC.patient.id}`);
    expect(flow.count, 'F2: actions to find and open a record').toBeLessThanOrEqual(3);
  });
});

test.describe('navigation by permission and plan (R9)', () => {
  test('staff with patients.read get the search first', async ({ page }) => {
    await simulateApi(page, staffRoutes(SYNTHETIC.staffAccount));
    await logIn(page);
    await expect(page).toHaveURL('/patients');

    expect(await navLabels(page)).toEqual(['Pacientes', 'Personal', 'Roles', 'Estado del sistema']);
    await expect(
      page.getByRole('navigation', { name: 'Navegación principal' }).getByRole('link', {
        name: 'Pacientes',
      }),
    ).toHaveAttribute('aria-current', 'page');
    await restPointer(page);
    await expect(page).toHaveScreenshot('navigation.png');
    await expectNoAxeViolations(page);
  });

  for (const [name, account] of [
    ['without patients.read', SYNTHETIC.staffWithoutPatients],
    ['in a plan without the patients module', SYNTHETIC.staffPlanWithoutPatients],
  ] as const) {
    test(`staff ${name} are not offered the search and cannot open it`, async ({ page }) => {
      await simulateApi(page, {
        ...staffRoutes(account),
        'GET /staff-users': { body: { items: [], page: 0, size: 20, totalItems: 0 } },
        'GET /roles': { body: [] },
      });
      await logIn(page);
      // Both accounts keep the staff entry, so that is where they land.
      await expect(page).toHaveURL('/staff');

      expect(await navLabels(page)).not.toContain('Pacientes');

      await page.goto('/patients');
      await expect(page).toHaveURL('/forbidden');
      await expect(page.getByRole('heading', { level: 1, name: 'Sin permiso' })).toBeVisible();
    });
  }
});
