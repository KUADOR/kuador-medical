// Secondary account screens migrated in MEDK-44 (only the look changes): password recovery,
// new password, invitation, email verification, clinic signup and the mandatory password
// change. Screenshot of the content in light, dark and mobile, and axe with color contrast.
// Synthetic data only, against the simulated API.
import { expect, test } from '@playwright/test';
import { STAFF_OWNER, captureScreen, signIn } from './support/secondary-screens';
import { simulateApi } from './support/simulated-api';

const TOKEN = 'synthetic-link-token-0123456789';

test.describe('secondary account screens', () => {
  test('forgot password', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/forgot-password');
    await expect(page.getByRole('heading', { level: 1, name: 'Recuperar contraseña' })).toBeVisible();

    await captureScreen(page, api, 'forgot-password.png');
  });

  test('forgot password with a validation error', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/forgot-password');
    await page.getByRole('button', { name: 'Enviar enlace' }).click();
    await expect(page.getByText('Este campo es obligatorio.')).toBeVisible();

    await captureScreen(page, api, 'forgot-password-errors.png');
  });

  test('forgot password sent', async ({ page }) => {
    const api = await simulateApi(page, {
      'POST /auth/password-reset/request': { status: 202, body: null },
    });
    await page.goto('/forgot-password');
    await page.getByLabel('Correo').fill('persona@clinica-ficticia.example.test');
    await page.getByRole('button', { name: 'Enviar enlace' }).click();
    await expect(page.getByText('te enviamos un enlace', { exact: false })).toBeVisible();

    await captureScreen(page, api, 'forgot-password-sent.png');
  });

  test('new password', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto(`/password-reset#t=${TOKEN}`);
    await expect(
      page.getByRole('heading', { level: 1, name: 'Crear contraseña nueva' }),
    ).toBeVisible();

    await captureScreen(page, api, 'reset-password.png');
  });

  test('new password with validation errors', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto(`/password-reset#t=${TOKEN}`);
    await page.getByLabel('Contraseña nueva', { exact: true }).fill('corta');
    await page.getByLabel('Repite la contraseña nueva').fill('distinta');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByText('Las contraseñas no coinciden.')).toBeVisible();

    await captureScreen(page, api, 'reset-password-errors.png');
  });

  test('new password with an invalid link', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/password-reset');
    await expect(page.getByRole('link', { name: 'Pedir un enlace nuevo' })).toBeVisible();

    await captureScreen(page, api, 'reset-password-invalid.png');
  });

  test('invitation', async ({ page }) => {
    const api = await simulateApi(page, {
      'POST /auth/invitations/preview': {
        body: {
          clinicName: 'Clínica Ficticia',
          maskedEmail: 'p***@clinica-ficticia.example.test',
          kind: 'STAFF',
          expiresAt: '2030-01-04T15:00:00Z',
        },
      },
    });
    await page.goto(`/invitation#t=${TOKEN}`);
    await expect(page.getByText('Clínica Ficticia')).toBeVisible();

    await captureScreen(page, api, 'accept-invitation.png');
  });

  test('email verification', async ({ page }) => {
    const api = await simulateApi(page, {
      'POST /auth/email-verification/confirm': { status: 204, body: null },
    });
    await page.goto(`/email-verification#t=${TOKEN}`);
    await expect(page.getByText('Tu correo quedó verificado', { exact: false })).toBeVisible();

    await captureScreen(page, api, 'verify-email.png');
  });

  test('email verification with an invalid link', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/email-verification');
    await expect(page.getByRole('alert')).toBeVisible();

    await captureScreen(page, api, 'verify-email-invalid.png');
  });

  test('clinic signup', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/signup');
    await expect(page.getByRole('heading', { level: 1, name: 'Registrar clínica' })).toBeVisible();

    await captureScreen(page, api, 'signup.png');
  });

  test('clinic signup with validation errors', async ({ page }) => {
    const api = await simulateApi(page);
    await page.goto('/signup');
    await page.getByRole('button', { name: 'Registrar clínica' }).click();
    await expect(page.getByText('Este campo es obligatorio.').first()).toBeVisible();

    await captureScreen(page, api, 'signup-errors.png');
  });

  test('mandatory password change', async ({ page }) => {
    const api = await signIn(page, { ...STAFF_OWNER, mustChangePassword: true });
    await page.goto('/change-password');
    await expect(page.getByRole('heading', { level: 1, name: 'Cambiar contraseña' })).toBeVisible();

    await captureScreen(page, api, 'change-password.png');
  });
});
