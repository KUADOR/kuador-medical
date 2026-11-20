import { Component, signal } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, polyfillDialog, render, settle } from '../../../testing/design-system';
import { KdButton } from '../button/button';
import { KdDialog } from './dialog';

@Component({
  imports: [KdDialog, KdButton],
  template: `
    <button kdButton type="button" (click)="open.set(true)">Cancelar cita</button>
    <kd-dialog
      heading="¿Cancelar la cita?"
      description="El horario quedará libre para otro paciente."
      [(open)]="open"
      (closed)="closedCount = closedCount + 1"
    >
      <div kdDialogActions>
        <button kdButton variant="tertiary" type="button" (click)="open.set(false)">Volver</button>
        <button kdButton variant="danger" type="button">Cancelar cita</button>
      </div>
    </kd-dialog>
  `,
})
class Host {
  readonly open = signal(false);
  closedCount = 0;
}

const dialogOf = (root: HTMLElement) => root.querySelector('dialog')!;

describe('KdDialog', () => {
  beforeAll(() => polyfillDialog());

  it('uses a native dialog named by its heading and described by its text', async () => {
    const dialog = dialogOf(el(await render(Host)));

    expect(dialog.open).toBe(false);
    const title = dialog.querySelector(`#${dialog.getAttribute('aria-labelledby')}`);
    expect(title?.tagName).toBe('H2');
    expect(title?.textContent?.trim()).toBe('¿Cancelar la cita?');
    expect(dialog.querySelector(`#${dialog.getAttribute('aria-describedby')}`)?.textContent).toContain(
      'El horario quedará libre',
    );
  });

  it('opens as a modal when `open` becomes true and closes when it becomes false', async () => {
    const fixture = await render(Host);
    const showModal = vi.spyOn(HTMLDialogElement.prototype, 'showModal');

    fixture.componentInstance.open.set(true);
    await settle(fixture);
    expect(showModal).toHaveBeenCalledTimes(1);
    expect(dialogOf(el(fixture)).open).toBe(true);

    fixture.componentInstance.open.set(false);
    await settle(fixture);
    expect(dialogOf(el(fixture)).open).toBe(false);
    expect(fixture.componentInstance.closedCount).toBe(1);
    showModal.mockRestore();
  });

  it('syncs `open` back when the browser closes it (Escape) or with the close button', async () => {
    const fixture = await render(Host);
    fixture.componentInstance.open.set(true);
    await settle(fixture);

    dialogOf(el(fixture)).close();
    await settle(fixture);
    expect(fixture.componentInstance.open()).toBe(false);

    fixture.componentInstance.open.set(true);
    await settle(fixture);
    el(fixture).querySelector<HTMLButtonElement>('[aria-label="Cerrar"]')!.click();
    await settle(fixture);
    expect(fixture.componentInstance.open()).toBe(false);
    expect(fixture.componentInstance.closedCount).toBe(2);
  });

  it('has no accessibility violations when open (axe)', async () => {
    const fixture = await render(Host);
    fixture.componentInstance.open.set(true);
    await settle(fixture);

    await expectNoAxeViolations(el(fixture));
  });
});
