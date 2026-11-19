// Test-only helpers: the real routes, guards and interceptor against HttpTestingController.
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import type { AuthenticatedResult, CurrentSession } from '../app/core/api/api-types';
import { routes } from '../app/app.routes';
import { authInterceptor } from '../app/core/session/auth.interceptor';
import { SessionStore } from '../app/core/session/session.store';
import { LOGIN } from './fixtures';

export interface AppHarness {
  harness: RouterTestingHarness;
  backend: HttpTestingController;
  router: Router;
  session: SessionStore;
  /** Root element of the routed page. */
  page(): HTMLElement;
  /** Lets pending promises and change detection settle. */
  settle(): Promise<void>;
}

export async function createAppHarness(): Promise<AppHarness> {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideRouter(routes, withComponentInputBinding()),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  const harness = await RouterTestingHarness.create();
  const settle = async () => {
    // A macrotask flushes every pending microtask (HTTP callbacks, guards, navigation).
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  return {
    harness,
    backend: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
    session: TestBed.inject(SessionStore),
    page: () => harness.routeNativeElement as HTMLElement,
    settle,
  };
}

/** Starts a session whose GET /me answers with the given account. */
export async function signIn(
  app: AppHarness,
  account: CurrentSession,
  login: AuthenticatedResult = { ...LOGIN, mustChangePassword: account.mustChangePassword },
): Promise<void> {
  app.session.start(login);
  const loading = app.session.ensureAccount();
  app.backend.expectOne('/api/v1/me').flush(account);
  await loading;
}

export function byTestId(root: HTMLElement, id: string): HTMLElement | null {
  return root.querySelector<HTMLElement>(`[data-testid="${id}"]`);
}

export function allByTestId(root: HTMLElement, id: string): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(`[data-testid="${id}"]`));
}

export function type(root: HTMLElement, selector: string, value: string): void {
  const input = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector);
  if (!input) {
    throw new Error(`No field ${selector}`);
  }
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

export function submit(root: HTMLElement): void {
  const form = root.querySelector('form');
  if (!form) {
    throw new Error('No form');
  }
  form.dispatchEvent(new Event('submit'));
}

export function buttonByText(root: HTMLElement, text: string): HTMLButtonElement {
  const button = Array.from(root.querySelectorAll('button')).find((b) =>
    b.textContent?.trim().startsWith(text),
  );
  if (!button) {
    throw new Error(`No button "${text}"`);
  }
  return button;
}
