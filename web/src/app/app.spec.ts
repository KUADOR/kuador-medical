import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';
import { SessionStore } from './core/session/session.store';
import { expectNoAxeViolations } from '../testing/axe';
import {
  FULL_ACCOUNT,
  LOGIN,
  RECEPTION_ACCOUNT,
  STAFF_ADMIN_ACCOUNT,
  SUPER_ADMIN,
} from '../testing/fixtures';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('renders the design system shell with a skip link and the main landmark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('kd-shell')).not.toBeNull();
    expect(root.querySelector('.kd-shell__skip')?.getAttribute('href')).toBe('#kd-main');
    expect(root.querySelector('main#kd-main')).not.toBeNull();
    expect(root.querySelector('kd-nav nav')?.getAttribute('aria-label')).toBe(
      'Navegación principal',
    );
  });

  it('opens the status page by default', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(harness.routeNativeElement?.textContent).toContain('Estado del sistema');
    TestBed.inject(HttpTestingController).expectOne('/api/v1/system/info').flush({});
  });

  it('shows a Spanish not-found page for unknown routes', async () => {
    const harness = await RouterTestingHarness.create('/does-not-exist');
    expect(harness.routeNativeElement?.textContent).toContain('Página no encontrada');
  });

  it('keeps the password change page closed without a session', async () => {
    const harness = await RouterTestingHarness.create('/change-password');
    expect(harness.routeNativeElement?.textContent).toContain('Ingresar');
  });

  async function shellWith(account: typeof FULL_ACCOUNT) {
    TestBed.inject(SessionStore).start({
      ...LOGIN,
      mustChangePassword: account.mustChangePassword,
    });
    const fixture = TestBed.createComponent(App);
    TestBed.inject(HttpTestingController).expectOne('/api/v1/me').flush(account);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function navLabels(root: HTMLElement): string[] {
    return Array.from(root.querySelectorAll('kd-nav a')).map((a) => a.textContent?.trim() ?? '');
  }

  it('offers login and signup to visitors', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(navLabels(root)).toEqual(['Estado del sistema']);
    expect(root.querySelector('.kd-shell__account')?.textContent).toContain('Ingresar');
    expect(root.querySelector('.kd-shell__account')?.textContent).toContain('Registrar clínica');
  });

  it('builds the clinic menu from the permissions of GET /me', async () => {
    const root = await shellWith(FULL_ACCOUNT);

    expect(navLabels(root)).toEqual(['Roles', 'Estado del sistema']);
    expect(root.querySelector('[data-testid="account-name"]')?.textContent).toContain(
      'Test Owner · Test Clinic',
    );
  });

  it('shows the SUPER_ADMIN only the platform catalog', async () => {
    const root = await shellWith(SUPER_ADMIN);
    expect(navLabels(root)).toEqual(['Catálogo de permisos', 'Estado del sistema']);
  });

  it('hides the menu while the password change is pending', async () => {
    const root = await shellWith({ ...SUPER_ADMIN, mustChangePassword: true });
    expect(navLabels(root)).toEqual(['Estado del sistema']);
  });

  it('hides the modules the clinic plan does not enable', async () => {
    const root = await shellWith({ ...FULL_ACCOUNT, enabledModules: ['patients'] });
    expect(navLabels(root)).toEqual(['Estado del sistema']);
  });

  it('offers the patient search first, with its icon, to staff who may read patients', async () => {
    const root = await shellWith(STAFF_ADMIN_ACCOUNT);

    expect(navLabels(root)).toEqual(['Pacientes', 'Personal', 'Roles', 'Estado del sistema']);
    expect(root.querySelector('kd-nav a[href="/patients"] kd-icon')).not.toBeNull();
  });

  it('does not offer the patient search without patients.read (R9)', async () => {
    const root = await shellWith({
      ...STAFF_ADMIN_ACCOUNT,
      permissions: STAFF_ADMIN_ACCOUNT.permissions.filter((code) => code !== 'patients.read'),
    });
    expect(navLabels(root)).toEqual(['Personal', 'Roles', 'Estado del sistema']);
  });

  it('does not offer the patient search when the plan lacks the patients module (R9)', async () => {
    const root = await shellWith({ ...RECEPTION_ACCOUNT, enabledModules: ['identity'] });
    expect(navLabels(root)).toEqual(['Estado del sistema']);
  });

  it('has no accessibility violations for visitors and staff (axe)', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
    fixture.destroy();

    await expectNoAxeViolations(await shellWith(STAFF_ADMIN_ACCOUNT));
  });

  it('shows the active branch of the session', async () => {
    const root = await shellWith(FULL_ACCOUNT);
    expect(root.querySelector('[data-testid="active-branch"]')?.textContent).toContain(
      'Main branch',
    );
  });

  it('logs out from the header and revokes the refresh token', async () => {
    const root = await shellWith(FULL_ACCOUNT);
    (root.querySelector('[data-testid="logout"]') as HTMLButtonElement).click();
    await Promise.resolve();

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    const logout = TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/logout');
    expect(logout.request.body).toEqual({ refreshToken: 'test-refresh' });
    logout.flush(null, { status: 204, statusText: 'No Content' });
  });
});
