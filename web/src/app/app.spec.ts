import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';

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

  it('renders the shell with a skip link and the main landmark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.skip-link')?.getAttribute('href')).toBe('#main-content');
    expect(root.querySelector('main#main-content')).not.toBeNull();
    expect(root.querySelector('nav')?.getAttribute('aria-label')).toBe('Navegación principal');
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
});
