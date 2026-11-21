// MEDK-75: web walk against the real api, end to end:
// clinic onboarding → OWNER invitation → mandatory TOTP → suspension (read-only + export) →
// cancellation → reactivation → second cancellation → two-step purge.
// Synthetic data only (example.test addresses, random RUC). Credentials come from the environment
// and are never printed. See README.md in this folder.
import { type APIRequestContext, type Page, expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHmac, randomInt, randomUUID } from 'node:crypto';

const env = (name: string, fallback?: string): string => {
  const value = process.env[name] ?? fallback;
  if (!value) throw new Error(`Set ${name} (see e2e/integration/README.md)`);
  return value;
};

const API_URL = env('E2E_API_URL', 'http://localhost:8080/api/v1');
const MAILPIT_URL = env('E2E_MAILPIT_URL', 'http://localhost:8025');
const ADMIN_EMAIL = env('E2E_SUPER_ADMIN_EMAIL');
const ADMIN_PASSWORD = env('E2E_SUPER_ADMIN_PASSWORD');
// Optional: shell command that ends the retention period of a tenant in a disposable database,
// with {tenantId} as placeholder. Without it the purge step is skipped (retention lasts 90 days).
const EXPIRE_RETENTION_COMMAND = process.env['E2E_EXPIRE_RETENTION_COMMAND'] ?? '';

/** RFC 6238 TOTP (SHA-1, 6 digits, 30 s), the same parameters the api enrolls. */
function totp(base32Secret: string, at = Date.now()): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const char of base32Secret.replace(/[\s=]/g, '').toUpperCase()) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, '0');
  }
  const key = Buffer.from(bits.match(/.{8}/g)!.map((byte) => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)));
  const hmac = createHmac('sha1', key).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  return String((hmac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

/** Waits for the next TOTP step, so a code is never reused within the same step. */
async function nextTotpStep(): Promise<void> {
  const msLeft = 30_000 - (Date.now() % 30_000);
  await new Promise((resolve) => setTimeout(resolve, msLeft + 500));
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const response = await request.post(`${API_URL}/auth/login`, {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  expect(response.status(), 'SUPER_ADMIN login (password already changed)').toBe(200);
  const body = await response.json();
  expect(body.mustChangePassword).toBe(false);
  return body.accessToken as string;
}

/** Latest OWNER invitation token sent to `email` (Mailpit API). */
async function invitationToken(request: APIRequestContext, email: string): Promise<string> {
  let token = '';
  await expect
    .poll(
      async () => {
        const search = await request.get(`${MAILPIT_URL}/api/v1/search`, {
          params: { query: `to:${email}` },
        });
        const messages = (await search.json()).messages ?? [];
        if (!messages.length) return '';
        const message = await request.get(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`);
        const text: string = (await message.json()).Text ?? '';
        token = /\/owner-invitation#t=([A-Za-z0-9_-]+)/.exec(text)?.[1] ?? '';
        return token;
      },
      { message: 'OWNER invitation email in Mailpit', timeout: 15_000 },
    )
    .not.toBe('');
  return token;
}

async function login(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

async function transition(page: Page, target: string, reason: string): Promise<void> {
  const panel = page.getByTestId('transitions');
  await panel.locator('#transition-target').selectOption(target);
  await panel.locator('#transition-reason').fill(reason);
  await panel.getByTestId('review-transition').click();
  await panel.getByTestId('confirm-transition').click();
  await expect(panel.getByTestId('transition-notice')).toBeVisible();
}

test('SUPER_ADMIN and OWNER walk the whole clinic lifecycle against the real api', async ({
  browser,
  request,
}) => {
  const runId = randomUUID().slice(0, 8);
  const ownerEmail = `owner-${runId}@clinic.example.test`;
  const ownerPassword = `Owner-${randomUUID()}`;
  const ruc = `17${String(randomInt(0, 10 ** 8)).padStart(8, '0')}001`;

  // Setup through the api: a paid plan to leave the trial (plans have their own web tests).
  const token = await adminToken(request);
  const planCode = `E2E_${runId.toUpperCase()}`;
  const planResponse = await request.post(`${API_URL}/platform/plans`, {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': randomUUID() },
    data: {
      id: randomUUID(),
      code: planCode,
      name: `Plan ${planCode}`,
      trial: false,
      modules: ['AGENDA', 'PATIENTS', 'USERS'],
      limits: { MAX_USERS: 10, MAX_BRANCHES: 2 },
      prices: [
        { billingPeriod: 'MONTHLY', currency: 'USD', amount: '49.00' },
        { billingPeriod: 'ANNUAL', currency: 'USD', amount: '490.00' },
      ],
    },
  });
  expect(planResponse.status(), 'paid plan created').toBe(201);
  const planId: string = (await planResponse.json()).id;
  const plans = await request.get(`${API_URL}/platform/plans`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const trialPlanId: string = (await plans.json()).find(
    (plan: { trial: boolean }) => plan.trial,
  ).id;

  const adminContext = await browser.newContext();
  const admin = await adminContext.newPage();
  const ownerContext = await browser.newContext();
  const owner = await ownerContext.newPage();
  let tenantId = '';

  await test.step('1. Alta: the SUPER_ADMIN registers the clinic with the trial plan', async () => {
    await login(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
    await expect(admin).not.toHaveURL(/\/login/);
    await admin.goto('/platform/tenants/new');
    await admin.locator('#tenant-legal-name').fill(`Clínica E2E ${runId}`);
    await admin.locator('#tenant-ruc').fill(ruc);
    await admin.locator('#tenant-type').selectOption('MEDICAL');
    await admin.locator('#tenant-city').fill('Quito');
    await admin.locator('#tenant-address').fill('Av. Ejemplo 123');
    await admin.locator('#tenant-phone').fill('+593 2 000 0000');
    await admin.locator('#tenant-contact-email').fill(`contacto-${runId}@clinic.example.test`);
    await admin.locator('#tenant-plan').selectOption(trialPlanId);
    await admin.locator('#tenant-owner-name').fill('Owner E2E');
    await admin.locator('#tenant-owner-email').fill(ownerEmail);
    await admin.getByRole('button', { name: 'Registrar clínica y enviar invitación' }).click();
    await expect(admin).toHaveURL(/\/platform\/tenants\/[0-9a-f-]{36}$/);
    tenantId = admin.url().split('/').pop()!;
    await expect(admin.getByTestId('subscription-status')).toHaveText('Prueba');
  });

  let totpSecret = '';
  await test.step('2. Invitación: the OWNER activates the account from the email link', async () => {
    const invitation = await invitationToken(request, ownerEmail);
    await owner.goto(`/owner-invitation#t=${invitation}`);
    await expect(owner.getByTestId('invitation-summary')).toContainText(`Clínica E2E ${runId}`);
    await owner.locator('#owner-password').fill(ownerPassword);
    await owner.locator('#owner-confirm').fill(ownerPassword);
    await owner.locator('#owner-terms').check();
    await owner.locator('#owner-data-policy').check();
    await owner.getByRole('button', { name: 'Continuar' }).click();
    await expect(owner).toHaveURL(/\/two-factor\/enroll/);
  });

  await test.step('3. 2FA: the OWNER enrolls TOTP and must verify it on the next login', async () => {
    const secret = owner.getByTestId('two-factor-secret');
    await expect(secret).toBeVisible();
    totpSecret = ((await secret.textContent()) ?? '').replace(/\s/g, '');
    await owner.locator('#two-factor-code').fill(totp(totpSecret));
    await owner.getByRole('button', { name: 'Activar y continuar' }).click();
    await expect(owner).not.toHaveURL(/\/two-factor\//);

    await ownerContext.clearCookies();
    await owner.evaluate(() => {
      sessionStorage.clear();
      localStorage.clear();
    });
    await nextTotpStep();
    await login(owner, ownerEmail, ownerPassword);
    await expect(owner).toHaveURL(/\/two-factor\/verify/);
    await owner.locator('#two-factor-code').fill(totp(totpSecret));
    await owner.getByRole('button', { name: 'Verificar e ingresar' }).click();
    await expect(owner).not.toHaveURL(/\/two-factor\//);
    await expect(owner).not.toHaveURL(/\/login/);
  });

  await test.step('4. Suspensión: upgrade to a paid plan, activate and suspend', async () => {
    await admin.reload();
    const planChange = admin.getByTestId('plan-change');
    await planChange.locator('#plan-change-target').selectOption(planId);
    await planChange.locator('#plan-change-period').selectOption('MONTHLY');
    await planChange.getByTestId('preview-plan-change').click();
    await expect(planChange.getByTestId('preview-kind')).toBeVisible();
    await planChange.getByTestId('apply-plan-change').click();
    await expect(planChange.getByTestId('plan-change-notice')).toBeVisible();

    if ((await admin.getByTestId('subscription-status').textContent())?.trim() !== 'Activa') {
      await transition(admin, 'ACTIVE', 'Pago registrado manualmente (E2E)');
    }
    await expect(admin.getByTestId('subscription-status')).toHaveText('Activa');
    await transition(admin, 'SUSPENDED', 'Suspensión de prueba (E2E)');
    await expect(admin.getByTestId('subscription-status')).toHaveText('Suspendida');

    // The OWNER sees the read-only notice and can still export.
    await owner.goto('/data-export');
    await expect(owner.getByTestId('subscription-notice')).toBeVisible();
    await owner.locator('#export-format').selectOption('JSON');
    await owner.locator('#export-responsibility').check();
    await owner.getByRole('button', { name: 'Solicitar exportación' }).click();
    await expect(owner.getByTestId('export-ready')).toBeVisible({ timeout: 30_000 });
  });

  await test.step('5. Cancelación: the SUPER_ADMIN cancels; retention starts', async () => {
    await transition(admin, 'CANCELLED', 'Baja a pedido de la clínica (E2E)');
    await expect(admin.getByTestId('subscription-status')).toHaveText('Cancelada');
    await expect(admin.getByTestId('reactivation-window')).toBeVisible();
  });

  await test.step('6. Reactivación: manual, with reason, within the retention', async () => {
    await transition(admin, 'ACTIVE', 'Reactivación acordada con la clínica (E2E)');
    await expect(admin.getByTestId('subscription-status')).toHaveText('Activa');
    await owner.goto('/');
    await expect(owner.getByTestId('subscription-notice')).toHaveCount(0);
  });

  await test.step('7. Purga: cancel again, end the retention and purge in two steps', async () => {
    await transition(admin, 'CANCELLED', 'Baja definitiva solicitada (E2E)');
    await expect(admin.getByTestId('subscription-status')).toHaveText('Cancelada');
    test.skip(
      !EXPIRE_RETENTION_COMMAND,
      'Set E2E_EXPIRE_RETENTION_COMMAND to end the retention in a disposable database',
    );
    execFileSync('sh', ['-c', EXPIRE_RETENTION_COMMAND.replaceAll('{tenantId}', tenantId)], {
      stdio: 'ignore',
    });
    await admin.reload();
    const purge = admin.getByTestId('purge');
    await purge.locator('#purge-reason').fill('Retención cumplida (E2E)');
    await purge.getByTestId('request-purge').click();
    await expect(purge.getByTestId('purge-step-2')).toBeVisible();
    await purge.locator('#purge-ruc').fill(ruc);
    await purge.getByTestId('confirm-purge').click();
    await expect(purge.getByTestId('purge-confirmed')).toBeVisible();

    // The purged OWNER can no longer sign in.
    await ownerContext.clearCookies();
    await owner.evaluate(() => {
      sessionStorage.clear();
      localStorage.clear();
    });
    await login(owner, ownerEmail, ownerPassword);
    await expect(owner).toHaveURL(/\/login/);
  });

  await adminContext.close();
  await ownerContext.close();
});

test('activating a trial clinic without a paid plan explains the next step', async ({
  browser,
  request,
}) => {
  const token = await adminToken(request);
  const headers = { Authorization: `Bearer ${token}` };
  const plans = await (await request.get(`${API_URL}/platform/plans`, { headers })).json();
  const trialPlanId: string = plans.find((plan: { trial: boolean }) => plan.trial).id;
  const runId = randomUUID().slice(0, 8);
  const created = await request.post(`${API_URL}/platform/tenants`, {
    headers: { ...headers, 'Idempotency-Key': randomUUID() },
    data: {
      id: randomUUID(),
      legalName: `Clínica Prueba ${runId}`,
      ruc: `17${String(randomInt(0, 10 ** 8)).padStart(8, '0')}001`,
      clinicType: 'DENTAL',
      countryCode: 'EC',
      city: 'Quito',
      address: 'Av. Ejemplo 456',
      phone: '+593 2 000 0000',
      contactEmail: `contacto-${runId}@clinic.example.test`,
      planId: trialPlanId,
      billingPeriod: 'MONTHLY',
      owner: { fullName: 'Owner Prueba', email: `owner-${runId}@clinic.example.test` },
    },
  });
  expect(created.status(), 'trial clinic created').toBe(201);
  const tenantId: string = (await created.json()).id;

  const context = await browser.newContext();
  const admin = await context.newPage();
  await login(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(admin).not.toHaveURL(/\/login/);
  await admin.goto(`/platform/tenants/${tenantId}`);
  const panel = admin.getByTestId('transitions');
  await panel.locator('#transition-target').selectOption('ACTIVE');
  await panel.locator('#transition-reason').fill('Pago recibido (E2E)');
  await panel.getByTestId('review-transition').click();
  await panel.getByTestId('confirm-transition').click();
  await expect(panel.getByTestId('transition-failure')).toContainText(
    'Primero cambia la clínica a un plan pagado',
  );
  await expect(admin.getByTestId('subscription-status')).toHaveText('Prueba');
  await context.close();
});
