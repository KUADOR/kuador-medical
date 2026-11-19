import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { SystemInfo } from '../../core/api/api-types';
import { ERROR_MESSAGES, NETWORK_ERROR_MESSAGE } from '../../core/errors/error-messages';
import { StatusPage } from './status.page';

const UP: SystemInfo = {
  name: 'medical-kuador-api',
  version: '0.1.0',
  status: 'UP',
  database: 'UP',
};

describe('StatusPage', () => {
  let fixture: ComponentFixture<StatusPage>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StatusPage);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function text(testId: string): string | undefined {
    const element = (fixture.nativeElement as HTMLElement).querySelector(
      `[data-testid="${testId}"]`,
    );
    return element?.textContent?.trim();
  }

  async function respond(body: object, init?: { status: number; statusText: string }) {
    backend.expectOne('/api/v1/system/info').flush(body, init);
    await fixture.whenStable();
  }

  it('shows a loading message while it asks the API', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Consultando el estado del sistema',
    );
    backend.expectOne('/api/v1/system/info').flush(UP);
  });

  it('shows the system status from GET /system/info', async () => {
    await respond(UP);

    expect(text('app-name')).toBe('medical-kuador-api');
    expect(text('app-version')).toBe('0.1.0');
    expect(text('app-status')).toBe('Disponible');
    expect(text('db-status')).toBe('Disponible');
    expect(text('degraded')).toBeUndefined();
  });

  it('explains in Spanish when the database is down', async () => {
    await respond({ ...UP, database: 'DOWN' });

    expect(text('db-status')).toBe('No disponible');
    expect(text('degraded')).toContain('Algunos servicios no están disponibles');
  });

  it('shows the translated message and no internal details on a server error', async () => {
    await respond(
      {
        type: 'about:blank',
        title: 'Internal Server Error',
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'java.sql.SQLException: connection refused',
      },
      { status: 500, statusText: 'Internal Server Error' },
    );

    expect(text('error')).toBe(ERROR_MESSAGES.INTERNAL_ERROR);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('SQLException');
  });

  it('retries on demand after a network failure', async () => {
    backend.expectOne('/api/v1/system/info').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(text('error')).toBe(NETWORK_ERROR_MESSAGE);

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'button',
    ) as HTMLButtonElement;
    button.click();
    await respond(UP);

    expect(text('error')).toBeUndefined();
    expect(text('app-status')).toBe('Disponible');
  });
});
