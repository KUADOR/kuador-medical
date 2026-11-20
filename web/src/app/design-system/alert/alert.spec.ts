import { Component, signal } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render, settle } from '../../../testing/design-system';
import { KdAlert } from './alert';

@Component({
  imports: [KdAlert],
  template: `
    <kd-alert tone="ok">Cita agendada.</kd-alert>
    <kd-alert tone="error" heading="No pudimos guardar la cita">
      Revisa tu conexión e inténtalo de nuevo.
    </kd-alert>
    <kd-alert tone="warning">Tu sesión vence en 5 minutos.</kd-alert>
    @if (showInfo()) {
      <kd-alert dismissible (dismissed)="showInfo.set(false)">Hay una versión nueva.</kd-alert>
    }
  `,
})
class Host {
  readonly showInfo = signal(true);
}

describe('KdAlert', () => {
  it('announces errors as alerts and everything else as status', async () => {
    const alerts = el(await render(Host)).querySelectorAll('kd-alert');

    expect(alerts[0].getAttribute('role')).toBe('status');
    expect(alerts[1].getAttribute('role')).toBe('alert');
    expect(alerts[2].getAttribute('role')).toBe('status');
    expect(alerts[3].getAttribute('role')).toBe('status');
  });

  it('says the state with an icon and text, not only with color (R-08)', async () => {
    const alerts = el(await render(Host)).querySelectorAll('kd-alert');

    expect(alerts[0].classList).toContain('kd-alert--ok');
    expect(alerts[1].classList).toContain('kd-alert--error');
    expect(alerts[3].classList).toContain('kd-alert--info');
    for (const alert of Array.from(alerts)) {
      expect(alert.querySelector('kd-icon')).not.toBeNull();
    }
    expect(alerts[1].querySelector('.kd-alert__heading')?.textContent?.trim()).toBe(
      'No pudimos guardar la cita',
    );
  });

  it('can be dismissed with a labelled button', async () => {
    const fixture = await render(Host);
    const button = el(fixture).querySelector<HTMLButtonElement>('[aria-label="Cerrar aviso"]')!;

    button.click();
    await settle(fixture);

    expect(el(fixture).querySelectorAll('kd-alert')).toHaveLength(3);
    expect(el(fixture).querySelectorAll('[aria-label="Cerrar aviso"]')).toHaveLength(0);
  });

  it('has no accessibility violations (axe)', async () => {
    await expectNoAxeViolations(el(await render(Host)));
  });
});
