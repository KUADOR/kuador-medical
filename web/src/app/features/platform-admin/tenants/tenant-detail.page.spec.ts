import type { Subscription, Tenant } from '../../../core/api/api-types';
import { ERROR_MESSAGES } from '../../../core/errors/error-messages';
import { FULL_ACCOUNT, problem } from '../../../../testing/fixtures';
import {
  AppHarness,
  allByTestId,
  byTestId,
  createAppHarness,
  signIn,
  type,
} from '../../../../testing/app-harness';
import { axeViolations } from '../../../../testing/axe';
import { expectMigratedScreen } from '../../../../testing/migrated-screen';
import {
  ACTIVE_OWNER_TENANT,
  ACTIVE_SUBSCRIPTION,
  CANCELLED_REACTIVABLE,
  CANCELLED_RETENTION_OVER,
  DOWNGRADE_PREVIEW,
  PLANS,
  TENANTS_ADMIN,
  PREMIUM_PLAN_ID,
  STANDARD_PLAN_ID,
  SUSPENDED_SUBSCRIPTION,
  TENANT,
  TENANTS_ONLY_ADMIN,
  TENANT_ID,
  UPGRADE_PREVIEW,
} from '../../../../testing/platform-tenant-fixtures';
import { RUC_MISMATCH_MESSAGE } from './panels/purge-panel';
import {
  ACTIVATION_NEEDS_PAID_PLAN,
  ACTIVATION_NEEDS_PAID_PLAN_TRIAL,
} from './panels/transition-panel';
import type { CurrentSession } from '../../../core/api/api-types';

const BASE = `/api/v1/platform/tenants/${TENANT_ID}`;

describe('TenantDetailPage', () => {
  let app: AppHarness;

  beforeEach(async () => {
    app = await createAppHarness();
  });

  afterEach(() => app.backend.verify());

  async function open(
    subscription: Subscription = ACTIVE_SUBSCRIPTION,
    tenant: Tenant = TENANT,
    account: CurrentSession = TENANTS_ADMIN,
  ) {
    await signIn(app, account);
    await app.harness.navigateByUrl(`/platform/tenants/${TENANT_ID}`);
    await app.settle();
    app.backend.expectOne(BASE).flush(tenant);
    app.backend.expectOne(`${BASE}/subscription`).flush(subscription);
    await app.settle();
    flushPlansIfAsked();
    await app.settle();
  }

  function flushPlansIfAsked(): void {
    for (const request of app.backend.match('/api/v1/platform/plans?status=ACTIVE')) {
      request.flush(PLANS);
    }
  }

  /** The page reads both resources again after an action. */
  async function flushRefresh(subscription: Subscription, tenant: Tenant = TENANT) {
    await app.settle();
    for (const request of app.backend.match(BASE)) {
      request.flush(tenant);
    }
    for (const request of app.backend.match(`${BASE}/subscription`)) {
      request.flush(subscription);
    }
    await app.settle();
    flushPlansIfAsked();
    await app.settle();
  }

  function select(selector: string, value: string): void {
    const element = app.page().querySelector(selector) as HTMLSelectElement;
    element.value = value;
    element.dispatchEvent(new Event('change'));
  }

  function submitIn(testId: string): void {
    const form =
      byTestId(app.page(), testId)?.querySelector('form') ?? byTestId(app.page(), testId);
    (form as HTMLFormElement).dispatchEvent(new Event('submit'));
  }

  function click(testId: string): void {
    const button = byTestId(app.page(), testId);
    if (!button) {
      throw new Error(`No ${testId}`);
    }
    button.click();
  }

  function text(testId: string): string | undefined {
    return byTestId(app.page(), testId)?.textContent?.replace(/\s+/g, ' ').trim();
  }

  // ------------------------------------------------------------------ data and history
  it('shows the clinic, its OWNER and its subscription with the contracted price (CA 15, H6)', async () => {
    await open();

    expect(app.page().querySelector('h1')?.textContent?.trim()).toBe('Example Clinic');
    expect(text('status')).toBe('Activa');
    expect(text('ruc')).toBe('0000000000001');
    expect(text('owner-email')).toBe('j***@example.com');
    expect(text('two-factor')).toBe('Sin configurar');
    expect(text('invitation')).toContain('Pendiente');
    expect(text('invitation')).toContain('vence el 4 de enero de 2030');
    expect(text('plan')).toBe('Standard');
    expect(text('price')).toBe('49,00 USD sin IVA');
    expect(text('access-mode')).toBe('Acceso completo según el plan');
    expect(text('price-lock')).toBe('No');
  });

  it('shows the history of contracted periods and transitions', async () => {
    await open();

    const periods = allByTestId(app.page(), 'period-row').map((row) =>
      Array.from(row.querySelectorAll('th, td')).map((cell) => cell.textContent?.trim()),
    );
    expect(periods).toEqual([
      ['Standard', '1 de enero de 2030', '1 de febrero de 2030', '49,00 USD'],
    ]);
    const transitions = allByTestId(app.page(), 'transition-row').map((row) =>
      Array.from(row.querySelectorAll('th, td')).map((cell) =>
        cell.textContent?.replace(/\s+/g, ' ').trim(),
      ),
    );
    expect(transitions).toEqual([
      ['1 de enero de 2030 a las 0:00', 'Prueba → Activa', 'Pago recibido', 'Sistema', '—'],
    ]);
  });

  it('shows a trial without cost and a scheduled plan change', async () => {
    await open({
      ...ACTIVE_SUBSCRIPTION,
      status: 'TRIAL',
      priceAmount: null,
      periods: [],
      scheduledPlanChange: {
        id: '5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a',
        kind: 'DOWNGRADE',
        status: 'SCHEDULED',
        fromPlan: { id: PREMIUM_PLAN_ID, code: 'PREMIUM', name: 'Premium' },
        toPlan: { id: STANDARD_PLAN_ID, code: 'STANDARD', name: 'Standard' },
        billingPeriod: 'MONTHLY',
        requestedAt: '2030-01-16T15:00:00Z',
        effectiveAt: '2030-02-01T05:00:00Z',
        proratedAmount: null,
        currency: 'USD',
      },
    });

    expect(text('price')).toBe('Sin costo durante la prueba');
    expect(text('scheduled-change')).toContain(
      'Cambio programado a Standard (Mensual) desde el 1 de febrero de 2030',
    );
    expect(text('no-periods')).toBe('Todavía no hay períodos pagados.');
  });

  it('shows a Spanish message when the clinic does not exist', async () => {
    await signIn(app, TENANTS_ADMIN);
    await app.harness.navigateByUrl(`/platform/tenants/${TENANT_ID}`);
    await app.settle();
    const { body, init } = problem(404, 'NOT_FOUND');
    app.backend.expectOne(BASE).flush(body, init);
    // forkJoin cancels the other request.
    app.backend.match(`${BASE}/subscription`);
    await app.settle();

    expect(text('load-error')).toBe(ERROR_MESSAGES.NOT_FOUND);
  });

  // ------------------------------------------------------------------ OWNER
  it('resends the OWNER invitation, optionally to a corrected email', async () => {
    await open();

    type(app.page(), '#owner-new-email', 'jane.doe@example.org');
    submitIn('resend-form');
    await app.settle();

    const request = app.backend.expectOne(`${BASE}/owner-invitations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.email).toBe('jane.doe@example.org');
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush(
      { ...TENANT.owner.invitation, maskedEmail: 'j***@example.org' },
      { status: 201, statusText: 'Created' },
    );
    await app.settle();
    app.backend.expectOne(BASE).flush(TENANT);
    await app.settle();

    expect(text('owner-notice')).toBe(
      'Enviamos una nueva invitación a j***@example.org. El enlace anterior ya no sirve.',
    );
  });

  it('does not offer to resend an accepted invitation', async () => {
    await open(ACTIVE_SUBSCRIPTION, ACTIVE_OWNER_TENANT);
    expect(byTestId(app.page(), 'resend-form')).toBeNull();
    expect(text('two-factor')).toBe('Configurada');
  });

  it('resets the second factor of the OWNER only with a reason', async () => {
    await open(ACTIVE_SUBSCRIPTION, ACTIVE_OWNER_TENANT);

    click('reset-two-factor');
    await app.settle();
    click('confirm-reset');
    await app.settle();
    app.backend.expectNone(`${BASE}/owner/two-factor-reset`);
    expect(app.page().querySelector('#owner-reset-reason')?.getAttribute('aria-invalid')).toBe(
      'true',
    );

    type(app.page(), '#owner-reset-reason', 'Lost phone; identity verified by video call.');
    click('confirm-reset');
    await app.settle();
    const request = app.backend.expectOne(`${BASE}/owner/two-factor-reset`);
    expect(request.request.body).toEqual({
      reason: 'Lost phone; identity verified by video call.',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await app.settle();
    app.backend.expectOne(BASE).flush(TENANT);
    await app.settle();

    expect(text('owner-notice')).toContain('Se restableció la verificación en dos pasos');
  });

  // ------------------------------------------------------------------ transitions
  it('suspends the clinic with a reason, after an explicit confirmation (CA 13 UI)', async () => {
    await open();

    const labels = Array.from(
      (app.page().querySelector('#transition-target') as HTMLSelectElement).options,
      (option) => option.textContent?.trim(),
    );
    expect(labels).toEqual(['Elige una acción', 'Suspender', 'Cancelar la suscripción']);

    select('#transition-target', 'SUSPENDED');
    submitIn('transition-form');
    await app.settle();
    // The reason is mandatory in the panel.
    expect(app.page().querySelector('#transition-reason')?.getAttribute('aria-invalid')).toBe(
      'true',
    );

    type(app.page(), '#transition-reason', 'Requested by the clinic owner.');
    submitIn('transition-form');
    await app.settle();
    expect(text('transition-consequence')).toContain('solo lectura y exportación');
    app.backend.expectNone(`${BASE}/subscription/transitions`);

    click('confirm-transition');
    await app.settle();
    const request = app.backend.expectOne(`${BASE}/subscription/transitions`);
    expect(request.request.body).toEqual({
      target: 'SUSPENDED',
      reason: 'Requested by the clinic owner.',
      version: 4,
    });
    request.flush(SUSPENDED_SUBSCRIPTION);
    await app.settle();
    app.backend.expectOne(BASE).flush({
      ...TENANT,
      subscription: { ...TENANT.subscription, status: 'SUSPENDED' },
    });
    await app.settle();

    expect(text('status')).toBe('Suspendida');
    expect(text('transition-notice')).toBe('Suspender: hecho. La suscripción quedó en Suspendida.');
    expect(text('plan-change-unavailable')).toContain('Prueba, Activa o Vencida');
  });

  it('reactivates a cancelled clinic inside the retention, with reason (D12, CA 13)', async () => {
    await open(CANCELLED_REACTIVABLE);

    expect(text('reactivation-window')).toContain(
      'hasta el fin de la retención el 7 de agosto de 2999',
    );
    select('#transition-target', 'ACTIVE');
    type(app.page(), '#transition-reason', 'Clinic paid by bank transfer.');
    submitIn('transition-form');
    await app.settle();
    expect(text('transition-consequence')).toContain('período nuevo desde hoy');
    expect(text('transition-consequence')).toContain('se cancelará cualquier solicitud de purga');

    click('confirm-transition');
    await app.settle();
    const request = app.backend.expectOne(`${BASE}/subscription/transitions`);
    expect(request.request.body).toEqual({
      target: 'ACTIVE',
      reason: 'Clinic paid by bank transfer.',
      version: 7,
    });
    request.flush({ ...ACTIVE_SUBSCRIPTION, reactivatedAt: '2030-06-01T15:00:00Z', version: 8 });
    await app.settle();
    app.backend.expectOne(BASE).flush(TENANT);
    await app.settle();
    flushPlansIfAsked();
    await app.settle();

    expect(text('status')).toBe('Activa');
    expect(text('transition-notice')).toBe(
      'Reactivar la clínica cancelada: hecho. La suscripción quedó en Activa.',
    );
  });

  it('does not offer the reactivation once the retention ended (D12, CA 14)', async () => {
    await open(CANCELLED_RETENTION_OVER);

    expect(byTestId(app.page(), 'transition-form')).toBeNull();
    expect(text('reactivation-closed')).toBe(
      'La retención terminó el 7 de agosto de 2025: ya no se puede reactivar.',
    );
  });

  it('explains a closed reactivation window answered by the api and offers to reload', async () => {
    await open(CANCELLED_REACTIVABLE);

    select('#transition-target', 'ACTIVE');
    type(app.page(), '#transition-reason', 'Clinic paid by bank transfer.');
    submitIn('transition-form');
    await app.settle();
    click('confirm-transition');
    await app.settle();
    const { body, init } = problem(409, 'REACTIVATION_WINDOW_CLOSED');
    app.backend.expectOne(`${BASE}/subscription/transitions`).flush(body, init);
    await app.settle();

    expect(byTestId(app.page(), 'transition-failure')?.textContent).toContain(
      ERROR_MESSAGES.REACTIVATION_WINDOW_CLOSED,
    );
    const reload = Array.from(
      byTestId(app.page(), 'transition-failure')!.querySelectorAll('button'),
    ).find((button) => button.textContent?.includes('Recargar'))!;
    reload.click();
    await flushRefresh(CANCELLED_RETENTION_OVER);

    expect(text('reactivation-closed')).toContain('ya no se puede reactivar');
  });

  it('explains that a trial clinic must move to a paid plan before activating it (MEDK-75)', async () => {
    // Real api: TRIAL offers ACTIVE, but activating a plan without a price for the billing
    // period answers 409 INVALID_SUBSCRIPTION_TRANSITION until the plan is changed.
    await open({
      ...ACTIVE_SUBSCRIPTION,
      status: 'TRIAL',
      priceAmount: null,
      periods: [],
      allowedTargets: ['ACTIVE', 'CANCELLED'],
    });

    select('#transition-target', 'ACTIVE');
    type(app.page(), '#transition-reason', 'Payment received by bank transfer.');
    submitIn('transition-form');
    await app.settle();
    click('confirm-transition');
    await app.settle();
    const { body, init } = problem(409, 'INVALID_SUBSCRIPTION_TRANSITION');
    app.backend.expectOne(`${BASE}/subscription/transitions`).flush(body, init);
    await app.settle();

    expect(text('transition-failure')).toBe(ACTIVATION_NEEDS_PAID_PLAN_TRIAL);
    const buttons = Array.from(
      byTestId(app.page(), 'transition-failure')!.querySelectorAll('button'),
    );
    expect(buttons.some((button) => button.textContent?.includes('Recargar'))).toBe(false);
  });

  it('explains that a suspended clinic on a plan without price cannot be activated yet', async () => {
    await open({ ...SUSPENDED_SUBSCRIPTION, allowedTargets: ['ACTIVE', 'CANCELLED'] });

    select('#transition-target', 'ACTIVE');
    type(app.page(), '#transition-reason', 'Payment received by bank transfer.');
    submitIn('transition-form');
    await app.settle();
    click('confirm-transition');
    await app.settle();
    const { body, init } = problem(409, 'INVALID_SUBSCRIPTION_TRANSITION');
    app.backend.expectOne(`${BASE}/subscription/transitions`).flush(body, init);
    await app.settle();

    expect(text('transition-failure')).toBe(ACTIVATION_NEEDS_PAID_PLAN);
  });

  it('keeps the generic message and the reload for other invalid transitions', async () => {
    await open();

    select('#transition-target', 'SUSPENDED');
    type(app.page(), '#transition-reason', 'Requested by the clinic owner.');
    submitIn('transition-form');
    await app.settle();
    click('confirm-transition');
    await app.settle();
    const { body, init } = problem(409, 'INVALID_SUBSCRIPTION_TRANSITION');
    app.backend.expectOne(`${BASE}/subscription/transitions`).flush(body, init);
    await app.settle();

    expect(text('transition-failure')).toContain(ERROR_MESSAGES.INVALID_SUBSCRIPTION_TRANSITION);
    const buttons = Array.from(
      byTestId(app.page(), 'transition-failure')!.querySelectorAll('button'),
    );
    expect(buttons.some((button) => button.textContent?.includes('Recargar'))).toBe(true);
  });

  // ------------------------------------------------------------------ plan change
  it('previews an upgrade with its prorated amount and applies it (CA 8 UI)', async () => {
    await open();

    const labels = Array.from(
      (app.page().querySelector('#plan-change-target') as HTMLSelectElement).options,
      (option) => option.textContent?.trim(),
    );
    // The trial plan is never offered as a target.
    expect(labels).toEqual([
      'Elige un plan',
      'Standard · Mensual 49,00 USD · Anual 490,00 USD',
      'Premium · Mensual 99,00 USD',
    ]);

    select('#plan-change-target', PREMIUM_PLAN_ID);
    submitIn('plan-change-form');
    await app.settle();
    const preview = app.backend.expectOne(`${BASE}/subscription/plan-change-preview`);
    expect(preview.request.body).toEqual({
      targetPlanId: PREMIUM_PLAN_ID,
      billingPeriod: 'MONTHLY',
    });
    preview.flush(UPGRADE_PREVIEW);
    await app.settle();

    expect(text('preview-kind')).toBe('Subida de plan: aplica de inmediato con prorrateo');
    expect(text('preview-prorated')).toBe('25,00 USD sin IVA');
    expect(byTestId(app.page(), 'preview-excess')).toBeNull();

    click('apply-plan-change');
    await app.settle();
    const change = app.backend.expectOne(`${BASE}/subscription/plan-changes`);
    expect(change.request.body).toMatchObject({
      targetPlanId: PREMIUM_PLAN_ID,
      billingPeriod: 'MONTHLY',
      version: 4,
    });
    expect(change.request.headers.get('Idempotency-Key')).toBeTruthy();
    change.flush(
      {
        id: '5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a',
        kind: 'UPGRADE',
        status: 'APPLIED',
        fromPlan: { id: STANDARD_PLAN_ID, code: 'STANDARD', name: 'Standard' },
        toPlan: { id: PREMIUM_PLAN_ID, code: 'PREMIUM', name: 'Premium' },
        billingPeriod: 'MONTHLY',
        requestedAt: '2030-01-16T15:00:00Z',
        effectiveAt: '2030-01-16T15:00:00Z',
        proratedAmount: '25.00',
        currency: 'USD',
      },
      { status: 201, statusText: 'Created' },
    );
    await flushRefresh({
      ...ACTIVE_SUBSCRIPTION,
      plan: { id: PREMIUM_PLAN_ID, code: 'PREMIUM', name: 'Premium' },
      version: 5,
    });

    expect(text('plan')).toBe('Premium');
    expect(text('plan-change-notice')).toBe(
      'Se aplicó el cambio a Premium. Prorrateo por los días restantes: 25,00 USD sin IVA.',
    );
  });

  it('shows the excess of a downgrade without blocking it (FR-013, CA 9)', async () => {
    await open();

    select('#plan-change-target', STANDARD_PLAN_ID);
    submitIn('plan-change-form');
    await app.settle();
    app.backend.expectOne(`${BASE}/subscription/plan-change-preview`).flush(DOWNGRADE_PREVIEW);
    await app.settle();

    expect(text('preview-kind')).toContain('Bajada de plan');
    expect(text('preview-effective')).toBe('1 de febrero de 2030');
    expect(
      allByTestId(app.page(), 'excess-item').map((item) =>
        item.textContent?.replace(/\s+/g, ' ').trim(),
      ),
    ).toEqual(['Usuarios: usa 8 y el plan permite 5.']);
    expect(text('preview-excess')).toContain('No se borra ningún dato');
    expect(text('preview-modules')).toContain('Farmacia.');
    const apply = byTestId(app.page(), 'apply-plan-change') as HTMLButtonElement;
    expect(apply.disabled).toBe(false);
    expect(apply.textContent?.trim()).toBe('Confirmar y programar');

    // Changing the target discards the preview.
    select('#plan-change-target', PREMIUM_PLAN_ID);
    await app.settle();
    expect(byTestId(app.page(), 'plan-preview')).toBeNull();
  });

  // ------------------------------------------------------------------ price lock
  it('locks and unlocks the contracted price (FR-006)', async () => {
    await open();

    type(app.page(), '#price-lock-until', '2999-12-31');
    type(app.page(), '#price-lock-reason', 'Launch agreement.');
    submitIn('price-lock-form');
    await app.settle();
    const lock = app.backend.expectOne(`${BASE}/subscription/price-lock`);
    expect(lock.request.method).toBe('PUT');
    expect(lock.request.body).toEqual({
      lockedUntil: '2999-12-31',
      reason: 'Launch agreement.',
      version: 4,
    });
    lock.flush({ ...ACTIVE_SUBSCRIPTION, priceLockedUntil: '2999-12-31', version: 5 });
    await app.settle();
    app.backend.expectOne(BASE).flush(TENANT);
    await app.settle();

    expect(text('price-lock')).toBe('Hasta el 31 de diciembre de 2999');
    expect(text('price-lock-notice')).toBe(
      'El precio contratado queda congelado hasta el 31 de diciembre de 2999.',
    );

    click('unlock-price');
    await app.settle();
    const unlock = app.backend.expectOne(`${BASE}/subscription/price-lock`);
    expect(unlock.request.body).toEqual({ lockedUntil: null, version: 5 });
    unlock.flush({ ...ACTIVE_SUBSCRIPTION, version: 6 });
    await app.settle();
    app.backend.expectOne(BASE).flush(TENANT);
    await app.settle();
    expect(text('price-lock')).toBe('No');
  });

  it('does not lock the price on a past day', async () => {
    await open();
    type(app.page(), '#price-lock-until', '2000-01-01');
    submitIn('price-lock-form');
    await app.settle();
    app.backend.expectNone(`${BASE}/subscription/price-lock`);
    expect(app.page().querySelector('#price-lock-until')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
  });

  // ------------------------------------------------------------------ purge
  it('purges in two audited steps, typing the RUC (D6, CA 15)', async () => {
    await open(CANCELLED_RETENTION_OVER);

    expect(text('purge')).toContain('La historia clínica no se purga hasta validar');
    submitIn('purge-step-1');
    await app.settle();
    app.backend.expectNone(`${BASE}/purge-requests`);

    type(app.page(), '#purge-reason', 'Retention ended; data downloaded.');
    submitIn('purge-step-1');
    await app.settle();
    const request = app.backend.expectOne(`${BASE}/purge-requests`);
    expect(request.request.body.reason).toBe('Retention ended; data downloaded.');
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush(
      {
        id: '7e6d5c4b-3a29-4180-9f7e-6d5c4b3a2918',
        status: 'REQUESTED',
        requestedAt: '2030-09-01T15:00:00Z',
        confirmedAt: null,
      },
      { status: 201, statusText: 'Created' },
    );
    await app.settle();
    expect(byTestId(app.page(), 'purge-step-2')).not.toBeNull();

    // A wrong RUC is caught before sending.
    type(app.page(), '#purge-ruc', '9999999999999');
    submitIn('purge-step-2');
    await app.settle();
    app.backend.expectNone(`${BASE}/purge-requests/7e6d5c4b-3a29-4180-9f7e-6d5c4b3a2918/confirm`);
    expect(app.page().textContent).toContain(RUC_MISMATCH_MESSAGE);

    type(app.page(), '#purge-ruc', '0000000000001');
    submitIn('purge-step-2');
    await app.settle();
    const confirm = app.backend.expectOne(
      `${BASE}/purge-requests/7e6d5c4b-3a29-4180-9f7e-6d5c4b3a2918/confirm`,
    );
    expect(confirm.request.body).toEqual({ rucConfirmation: '0000000000001' });
    confirm.flush({
      id: '7e6d5c4b-3a29-4180-9f7e-6d5c4b3a2918',
      status: 'CONFIRMED',
      requestedAt: '2030-09-01T15:00:00Z',
      confirmedAt: '2030-09-02T15:00:00Z',
    });
    await flushRefresh(CANCELLED_RETENTION_OVER);

    expect(text('purge-confirmed')).toContain('Purga confirmada el 2 de septiembre de 2030');
  });

  it('does not offer the purge during the retention', async () => {
    await open(CANCELLED_REACTIVABLE);
    expect(text('purge-unavailable')).toContain(
      'Se podrá purgar cuando termine la retención, el 7 de agosto de 2999.',
    );
    expect(byTestId(app.page(), 'purge-step-1')).toBeNull();
  });

  it('hides the purge without its permission and the plan change without the plans permission', async () => {
    await open(CANCELLED_RETENTION_OVER, TENANT, TENANTS_ONLY_ADMIN);
    expect(byTestId(app.page(), 'purge')).toBeNull();
    expect(byTestId(app.page(), 'plan-change')).toBeNull();
  });

  // ------------------------------------------------------------------ cross-cutting
  it('uses only the design system and has no accessibility violations', async () => {
    await open(CANCELLED_RETENTION_OVER, ACTIVE_OWNER_TENANT);
    expectMigratedScreen(app.page());
    expect(await axeViolations(app.page())).toEqual([]);
  });

  it('has no accessibility violations with the forms of an active clinic', async () => {
    await open();
    expect(await axeViolations(app.page())).toEqual([]);
  });

  it('is closed to clinic accounts and never asks the api (FR-026)', async () => {
    await signIn(app, FULL_ACCOUNT);
    await app.harness.navigateByUrl(`/platform/tenants/${TENANT_ID}`);
    await app.settle();
    expect(app.router.url).toBe('/forbidden');
    app.backend.expectNone(BASE);
  });
});
