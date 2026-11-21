import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { App } from '../../app';
import type { ClinicSubscription } from '../api/api-types';
import { routes } from '../../app.routes';
import { ERROR_MESSAGES, errorMessage } from '../errors/error-messages';
import { authInterceptor } from '../session/auth.interceptor';
import { SessionStore } from '../session/session.store';
import { expectNoAxeViolations } from '../../../testing/axe';
import { FULL_ACCOUNT, LOGIN, problem } from '../../../testing/fixtures';
import {
  ACTIVE_SUBSCRIPTION,
  CANCELLED_SUBSCRIPTION,
  OWNER_ACCOUNT,
  PAST_DUE_SUBSCRIPTION,
  SUSPENDED_SUBSCRIPTION,
} from '../../../testing/owner-fixtures';
import { subscriptionInterceptor } from './subscription.interceptor';
import { subscriptionNotice } from './subscription-notice';
import { SubscriptionService } from './subscription.service';

describe('subscriptionNotice', () => {
  it('warns about the grace period of an overdue subscription, with its end date', () => {
    const notice = subscriptionNotice(PAST_DUE_SUBSCRIPTION);
    expect(notice?.tone).toBe('warning');
    expect(notice?.heading).toBe('Suscripción vencida');
    expect(notice?.message).toContain('acceso completo hasta el 8 de febrero de 2030');
    expect(notice?.readOnly).toBe(false);
  });

  it('says a suspended clinic can only read and export', () => {
    const notice = subscriptionNotice(SUSPENDED_SUBSCRIPTION);
    expect(notice?.tone).toBe('error');
    expect(notice?.heading).toBe('Suscripción suspendida');
    expect(notice?.message).toContain('solo lectura');
    expect(notice?.message).toContain('exportar');
    expect(notice?.readOnly).toBe(true);
  });

  it('gives the end of the retention of a cancelled clinic', () => {
    const notice = subscriptionNotice(CANCELLED_SUBSCRIPTION);
    expect(notice?.heading).toBe('Suscripción cancelada');
    expect(notice?.message).toContain('retención hasta el 30 de junio de 2030');
    expect(notice?.readOnly).toBe(true);
  });

  it('says nothing for an active or trial subscription', () => {
    expect(subscriptionNotice(ACTIVE_SUBSCRIPTION)).toBeNull();
    expect(subscriptionNotice({ ...ACTIVE_SUBSCRIPTION, status: 'TRIAL' })).toBeNull();
    expect(subscriptionNotice(null)).toBeNull();
  });
});

describe('Subscription notice in the shell', () => {
  let backend: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([authInterceptor, subscriptionInterceptor])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function shellWith(account = OWNER_ACCOUNT) {
    TestBed.inject(SessionStore).start(LOGIN);
    const fixture = TestBed.createComponent(App);
    backend.expectOne('/api/v1/me').flush(account);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  async function answer(
    fixture: Awaited<ReturnType<typeof shellWith>>,
    subscription: ClinicSubscription,
  ) {
    backend.expectOne('/api/v1/tenant/subscription').flush(subscription);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function notice(root: HTMLElement): HTMLElement | null {
    return root.querySelector('[data-testid="subscription-notice"]');
  }

  it('shows nothing while the subscription is active', async () => {
    const root = await answer(await shellWith(), ACTIVE_SUBSCRIPTION);
    expect(notice(root)).toBeNull();
  });

  it('warns an overdue clinic that it keeps full access during the grace period', async () => {
    const root = await answer(await shellWith(), PAST_DUE_SUBSCRIPTION);
    expect(notice(root)?.textContent).toContain('Suscripción vencida');
    expect(notice(root)?.getAttribute('role')).toBe('status');
    expect(root.querySelector('[data-testid="subscription-export"]')).toBeNull();
  });

  it('tells a suspended clinic it is read-only and offers the export', async () => {
    const root = await answer(await shellWith(), SUSPENDED_SUBSCRIPTION);
    expect(notice(root)?.textContent).toContain('Suscripción suspendida');
    expect(notice(root)?.getAttribute('role')).toBe('alert');
    expect(root.querySelector('[data-testid="subscription-export"]')?.getAttribute('href')).toBe(
      '/data-export',
    );
    await expectNoAxeViolations(root);
  });

  it('shows the retention of a cancelled clinic', async () => {
    const root = await answer(await shellWith(), CANCELLED_SUBSCRIPTION);
    expect(notice(root)?.textContent).toContain('Suscripción cancelada');
    expect(notice(root)?.textContent).toContain('30 de junio de 2030');
  });

  it('does not ask for the subscription without subscription.read', async () => {
    const fixture = await shellWith(FULL_ACCOUNT);
    backend.expectNone('/api/v1/tenant/subscription');
    expect(notice(fixture.nativeElement as HTMLElement)).toBeNull();
    backend.match(() => true);
  });

  it('reloads the subscription when a write is rejected with TENANT_READ_ONLY', async () => {
    const fixture = await shellWith();
    await answer(fixture, ACTIVE_SUBSCRIPTION);

    const write = firstValueFrom(TestBed.inject(HttpClient).post('/api/v1/roles', {}));
    const { body, init } = problem(403, 'TENANT_READ_ONLY');
    backend.expectOne('/api/v1/roles').flush(body, init);
    const error = await write.catch((e: unknown) => e);

    expect(errorMessage(error)).toBe(ERROR_MESSAGES.TENANT_READ_ONLY);
    const root = await answer(fixture, SUSPENDED_SUBSCRIPTION);
    expect(notice(root)?.textContent).toContain('Suscripción suspendida');
    expect(TestBed.inject(SubscriptionService).readOnly()).toBe(true);
  });

  it('forgets the subscription on logout', async () => {
    const fixture = await shellWith();
    await answer(fixture, SUSPENDED_SUBSCRIPTION);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('[data-testid="logout"]')
      ?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(TestBed.inject(SubscriptionService).subscription()).toBeNull();
    expect(notice(fixture.nativeElement as HTMLElement)).toBeNull();
    backend.match(() => true);
  });
});

describe('Spanish messages of the plan errors', () => {
  it('explains the read-only mode and the plan limit without blaming data loss', () => {
    expect(ERROR_MESSAGES.TENANT_READ_ONLY).toContain('solo lectura');
    expect(ERROR_MESSAGES.TENANT_READ_ONLY).toContain('exportar');
    expect(ERROR_MESSAGES.PLAN_LIMIT_REACHED).toContain('límite de su plan');
    expect(ERROR_MESSAGES.PLAN_LIMIT_REACHED).toContain('No se borró ningún dato');
  });
});
