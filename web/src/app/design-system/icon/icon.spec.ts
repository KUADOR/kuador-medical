import { Component } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render } from '../../../testing/design-system';
import { KdIcon } from './icon';
import { ICONS } from './icons.generated';

@Component({
  imports: [KdIcon],
  template: `
    <p><kd-icon name="calendar" data-testid="decorative" /> Agenda</p>
    <kd-icon name="circle-alert" label="Error" data-testid="labelled" />
    <kd-icon name="search" [size]="20" data-testid="small" />
  `,
})
class Host {}

describe('KdIcon', () => {
  it('draws the shapes of the generated set as SVG elements', async () => {
    const root = el(await render(Host));
    const svg = root.querySelector('[data-testid="labelled"] svg')!;

    expect(svg.getAttribute('viewBox')).toBe('0 0 24 24');
    expect(svg.getAttribute('stroke')).toBe('currentColor');
    expect(svg.children).toHaveLength(ICONS['circle-alert'].length);
    expect(svg.querySelector('circle')?.getAttribute('r')).toBe('10');
  });

  it('is hidden from assistive technology unless it has a label', async () => {
    const root = el(await render(Host));
    const decorative = root.querySelector('[data-testid="decorative"]')!;
    const labelled = root.querySelector('[data-testid="labelled"]')!;

    expect(decorative.getAttribute('aria-hidden')).toBe('true');
    expect(decorative.getAttribute('role')).toBeNull();
    expect(labelled.getAttribute('role')).toBe('img');
    expect(labelled.getAttribute('aria-label')).toBe('Error');
    expect(labelled.getAttribute('aria-hidden')).toBeNull();
  });

  it('offers the 20 and 24 px sizes of the guide', async () => {
    const root = el(await render(Host));

    expect(root.querySelector('[data-testid="small"]')!.classList).toContain('kd-icon--20');
    expect(root.querySelector('[data-testid="decorative"]')!.classList).toContain('kd-icon--24');
  });

  it('has no accessibility violations (axe)', async () => {
    await expectNoAxeViolations(el(await render(Host)));
  });
});
