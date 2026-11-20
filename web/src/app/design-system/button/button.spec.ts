import { Component, signal } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render, settle } from '../../../testing/design-system';
import { KdButton, KdIconButton } from './button';

@Component({
  imports: [KdButton, KdIconButton],
  template: `
    <button kdButton variant="primary" icon="calendar-plus" type="button">Agendar cita</button>
    <button kdButton type="button">Ver detalle</button>
    <button kdButton variant="tertiary" type="button">Cancelar</button>
    <button kdButton variant="danger" type="button" [disabled]="disabled()">Eliminar rol</button>
    <a kdButton href="/patients">Ver pacientes</a>
    <button kdIconButton icon="pencil" label="Editar paciente" type="button"></button>
  `,
})
class Host {
  readonly disabled = signal(false);
}

describe('KdButton', () => {
  it('defaults to the secondary variant: primary must be chosen explicitly (R-02)', async () => {
    const buttons = el(await render(Host)).querySelectorAll('[kdButton]');

    expect(buttons[0].classList).toContain('kd-button--primary');
    expect(buttons[1].classList).toContain('kd-button--secondary');
    expect(buttons[2].classList).toContain('kd-button--tertiary');
    expect(buttons[3].classList).toContain('kd-button--danger');
    expect(buttons[4].classList).toContain('kd-button--secondary');
    expect(Array.from(buttons).every((b) => b.classList.contains('kd-button'))).toBe(true);
  });

  it('keeps the native element, so keyboard and disabled behave as usual', async () => {
    const fixture = await render(Host);
    const danger = el(fixture).querySelectorAll<HTMLButtonElement>('button[kdButton]')[3];
    expect(danger.disabled).toBe(false);

    fixture.componentInstance.disabled.set(true);
    await settle(fixture);

    expect(danger.disabled).toBe(true);
    expect(danger.tabIndex).toBe(0);
  });

  it('shows a decorative icon next to the text', async () => {
    const primary = el(await render(Host)).querySelector('[kdButton]')!;
    const icon = primary.querySelector('kd-icon')!;

    expect(icon.getAttribute('aria-hidden')).toBe('true');
    expect(primary.textContent?.trim()).toBe('Agendar cita');
  });

  it('gives the icon-only button an accessible name', async () => {
    const button = el(await render(Host)).querySelector('[kdIconButton]')!;

    expect(button.getAttribute('aria-label')).toBe('Editar paciente');
    expect(button.classList).toContain('kd-icon-button');
  });

  it('has no accessibility violations (axe)', async () => {
    await expectNoAxeViolations(el(await render(Host)));
  });
});
