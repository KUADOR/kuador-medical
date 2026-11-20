import { Component } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render } from '../../../testing/design-system';
import { KdButton } from '../button/button';
import { KdEmptyState } from './empty-state';
import { KdErrorState } from './error-state';
import { KdLoadingState } from './loading-state';

@Component({
  imports: [KdEmptyState, KdErrorState, KdLoadingState, KdButton],
  template: `
    <kd-empty-state heading="No tienes citas hoy" description="Las citas agendadas aparecerán aquí.">
      <button kdEmptyStateActions kdButton variant="primary" type="button">Agendar cita</button>
    </kd-empty-state>
    <kd-loading-state />
    <kd-loading-state label="Buscando pacientes…" />
    <kd-error-state (retry)="retries = retries + 1" />
    <kd-error-state heading="No encontramos al paciente" [retryable]="false" [headingLevel]="3" />
  `,
})
class Host {
  retries = 0;
}

describe('state components', () => {
  it('empty state: icon, heading, description and the projected action', async () => {
    const empty = el(await render(Host)).querySelector('kd-empty-state')!;

    expect(empty.querySelector('kd-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(empty.querySelector('h2')?.textContent?.trim()).toBe('No tienes citas hoy');
    expect(empty.textContent).toContain('Las citas agendadas aparecerán aquí.');
    expect(empty.querySelector('.kd-state__actions button')?.textContent?.trim()).toBe(
      'Agendar cita',
    );
  });

  it('loading state: a polite status with a default Spanish label', async () => {
    const loading = el(await render(Host)).querySelectorAll('kd-loading-state');

    expect(loading[0].getAttribute('role')).toBe('status');
    expect(loading[0].getAttribute('aria-live')).toBe('polite');
    expect(loading[0].textContent?.trim()).toBe('Cargando…');
    expect(loading[1].textContent?.trim()).toBe('Buscando pacientes…');
    expect(loading[0].querySelector('.kd-loading__spinner')?.getAttribute('aria-hidden')).toBe(
      'true',
    );
  });

  it('error state: an alert with what happened, what to do and a retry button', async () => {
    const fixture = await render(Host);
    const errors = el(fixture).querySelectorAll('kd-error-state');

    expect(errors[0].getAttribute('role')).toBe('alert');
    expect(errors[0].querySelector('h2')?.textContent?.trim()).toBe(
      'No pudimos cargar la información',
    );
    expect(errors[0].textContent).toContain('Revisa tu conexión e inténtalo de nuevo.');
    errors[0].querySelector<HTMLButtonElement>('button')!.click();
    expect(fixture.componentInstance.retries).toBe(1);

    expect(errors[1].querySelector('h3')?.textContent?.trim()).toBe('No encontramos al paciente');
    expect(errors[1].querySelector('button')).toBeNull();
  });

  it('have no accessibility violations (axe)', async () => {
    await expectNoAxeViolations(el(await render(Host)));
  });
});
