import { Component, signal } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render, settle } from '../../../testing/design-system';
import { KdActions } from '../actions/actions';
import { KdBadge } from '../badge/badge';
import { KdButton } from '../button/button';
import { KdDetails } from '../details/details';
import { KdField } from '../field/field';
import { KdFieldGroup } from '../field/field-group';
import { KdInput } from '../field/input';
import { KdSwitch } from '../switch/switch';
import { KdTable } from '../table/table';
import { KdPage } from './page';

@Component({
  imports: [
    KdPage,
    KdActions,
    KdBadge,
    KdButton,
    KdDetails,
    KdField,
    KdFieldGroup,
    KdInput,
    KdSwitch,
    KdTable,
  ],
  template: `
    <kd-page heading="Personal" description="Personas de la clínica." width="wide">
      <a kdPageBefore href="/staff">Volver</a>
      <a kdPageActions kdButton variant="primary" href="/staff/new">Invitar</a>

      <kd-badge tone="ok" icon="circle-check">Activo</kd-badge>

      <kd-table label="Personal de la clínica">
        <table>
          <caption>Personal</caption>
          <thead>
            <tr><th scope="col">Nombre</th><th scope="col">Estado</th></tr>
          </thead>
          <tbody>
            <tr><td>Persona Ficticia</td><td>Activo</td></tr>
          </tbody>
        </table>
      </kd-table>

      <dl kdDetails>
        <dt>Correo</dt>
        <dd>persona@clinica-ficticia.example.test</dd>
      </dl>

      <fieldset kdFieldGroup legend="Permisos" hint="Elige uno o más." groupId="perm" [error]="error()">
        <kd-field label="Leer pacientes">
          <input kdInput id="perm-read" type="checkbox" />
        </kd-field>
      </fieldset>

      <button kdSwitch type="button" [checked]="on()" (click)="on.set(!on())">Autorregistro</button>

      <kd-actions align="end">
        <button kdButton type="button">Cancelar</button>
      </kd-actions>
    </kd-page>
  `,
})
class Host {
  readonly error = signal<string | null>(null);
  readonly on = signal(false);
}

describe('KdPage and the layout pieces of MEDK-44', () => {
  it('names the page region after its h1 and projects the slots in order', async () => {
    const page = el(await render(Host)).querySelector('kd-page')!;
    const heading = page.querySelector('h1')!;

    expect(heading.textContent?.trim()).toBe('Personal');
    expect(page.getAttribute('role')).toBe('region');
    expect(page.getAttribute('aria-labelledby')).toBe(heading.id);
    expect(page.classList).toContain('kd-page--wide');
    expect(page.querySelector('.kd-page__description')?.textContent?.trim()).toBe(
      'Personas de la clínica.',
    );
    expect(page.querySelector('.kd-page__before')?.textContent?.trim()).toBe('Volver');
    expect(page.firstElementChild?.classList).toContain('kd-page__before');
    expect(page.querySelector('.kd-page__actions a')?.textContent?.trim()).toBe('Invitar');
  });

  it('wires the group hint and error to the fieldset', async () => {
    const fixture = await render(Host);
    const group = el(fixture).querySelector('fieldset')!;

    expect(group.querySelector('legend')?.textContent?.trim()).toBe('Permisos');
    expect(group.getAttribute('aria-describedby')).toBe('perm-hint');
    expect(group.getAttribute('aria-invalid')).toBeNull();

    fixture.componentInstance.error.set('Elige al menos un permiso.');
    await settle(fixture);

    expect(group.getAttribute('aria-describedby')).toBe('perm-hint perm-error');
    expect(group.getAttribute('aria-invalid')).toBe('true');
    expect(group.querySelector('#perm-error')?.textContent?.trim()).toBe(
      'Elige al menos un permiso.',
    );
  });

  it('sets the switch role and state', async () => {
    const fixture = await render(Host);
    const toggle = el(fixture).querySelector<HTMLButtonElement>('button[kdSwitch]')!;

    expect(toggle.getAttribute('role')).toBe('switch');
    expect(toggle.getAttribute('aria-checked')).toBe('false');

    toggle.click();
    await settle(fixture);

    expect(toggle.getAttribute('aria-checked')).toBe('true');
  });

  it('gives the table a named, focusable scroll region', async () => {
    const region = el(await render(Host)).querySelector('.kd-table__scroll')!;

    expect(region.getAttribute('role')).toBe('region');
    expect(region.getAttribute('tabindex')).toBe('0');
    expect(region.getAttribute('aria-label')).toBe('Personal de la clínica');
    expect(region.querySelector('table')).not.toBeNull();
  });

  it('marks the badge tone with a class and keeps the text', async () => {
    const badge = el(await render(Host)).querySelector('kd-badge')!;

    expect(badge.classList).toContain('kd-badge--ok');
    expect(badge.textContent?.trim()).toBe('Activo');
  });

  it('has no accessibility violations (axe)', async () => {
    const fixture = await render(Host);
    fixture.componentInstance.error.set('Elige al menos un permiso.');
    await settle(fixture);

    await expectNoAxeViolations(el(fixture));
  });
});
