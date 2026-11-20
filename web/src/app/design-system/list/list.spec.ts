import { Component } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render } from '../../../testing/design-system';
import { KdIconButton } from '../button/button';
import { KdList, KdListItem } from './list';

@Component({
  imports: [KdList, KdListItem, KdIconButton],
  template: `
    <kd-list label="Pacientes">
      <kd-list-item
        heading="Paciente Uno"
        description="Cédula 0000000001"
        meta="09:30"
        icon="user"
        link="/patients/1"
        [selected]="true"
      />
      <kd-list-item heading="Paciente Dos" description="Sin cita" link="/patients/2">
        <button kdListItemEnd kdIconButton icon="pencil" label="Editar Paciente Dos" type="button"></button>
      </kd-list-item>
      <kd-list-item heading="Sucursal Norte" />
    </kd-list>
  `,
})
class Host {}

describe('KdList and KdListItem', () => {
  it('exposes a labelled list of items', async () => {
    const root = el(await render(Host));
    const list = root.querySelector('kd-list')!;

    expect(list.getAttribute('role')).toBe('list');
    expect(list.getAttribute('aria-label')).toBe('Pacientes');
    expect(root.querySelectorAll('[role="listitem"]')).toHaveLength(3);
  });

  it('turns the row into a link when it has one, with heading, description and meta', async () => {
    const root = el(await render(Host));
    const link = root.querySelector<HTMLAnchorElement>('kd-list-item a')!;

    expect(link.getAttribute('href')).toBe('/patients/1');
    expect(link.querySelector('.kd-list-item__heading')?.textContent?.trim()).toBe('Paciente Uno');
    expect(link.textContent).toContain('Cédula 0000000001');
    expect(link.textContent).toContain('09:30');
  });

  it('marks the selected row with aria-current and a class (R-10)', async () => {
    const items = el(await render(Host)).querySelectorAll('kd-list-item');

    expect(items[0].classList).toContain('is-selected');
    expect(items[0].querySelector('a')?.getAttribute('aria-current')).toBe('true');
    expect(items[1].querySelector('a')?.getAttribute('aria-current')).toBeNull();
  });

  it('keeps end actions outside the row link (no nested interactive elements)', async () => {
    const second = el(await render(Host)).querySelectorAll('kd-list-item')[1];
    const action = second.querySelector('[kdIconButton]')!;

    expect(action.closest('a')).toBeNull();
    expect(action.getAttribute('aria-label')).toBe('Editar Paciente Dos');
  });

  it('renders a plain row without a link', async () => {
    const third = el(await render(Host)).querySelectorAll('kd-list-item')[2];

    expect(third.querySelector('a')).toBeNull();
    expect(third.textContent?.trim()).toBe('Sucursal Norte');
  });

  it('has no accessibility violations (axe)', async () => {
    await expectNoAxeViolations(el(await render(Host)));
  });
});
