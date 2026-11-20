import { Component } from '@angular/core';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render } from '../../../testing/design-system';
import { KdButton } from '../button/button';
import { KdCard } from './card';

@Component({
  imports: [KdCard, KdButton],
  template: `
    <h1>Ficha</h1>
    <kd-card heading="Próxima cita" description="Medicina general">
      <p>Lunes 23 de noviembre, 09:30</p>
      <div kdCardActions>
        <button kdButton type="button">Reprogramar</button>
      </div>
    </kd-card>
    <kd-card heading="Contactos" [headingLevel]="3" />
  `,
})
class Host {}

describe('KdCard', () => {
  it('names the card region after its heading', async () => {
    const card = el(await render(Host)).querySelector('kd-card')!;
    const heading = card.querySelector('h2')!;

    expect(heading.textContent?.trim()).toBe('Próxima cita');
    expect(card.getAttribute('role')).toBe('region');
    expect(card.getAttribute('aria-labelledby')).toBe(heading.id);
    expect(card.querySelector('.kd-card__description')?.textContent?.trim()).toBe('Medicina general');
  });

  it('uses the heading level the page asks for', async () => {
    const cards = el(await render(Host)).querySelectorAll('kd-card');

    expect(cards[1].querySelector('h3')?.textContent?.trim()).toBe('Contactos');
    expect(cards[1].querySelector('h2')).toBeNull();
  });

  it('projects the body and the actions footer', async () => {
    const card = el(await render(Host)).querySelector('kd-card')!;

    expect(card.querySelector('.kd-card__body p')?.textContent).toContain('09:30');
    expect(card.querySelector('.kd-card__actions button')?.textContent?.trim()).toBe('Reprogramar');
  });

  it('has no accessibility violations (axe)', async () => {
    await expectNoAxeViolations(el(await render(Host)));
  });
});
