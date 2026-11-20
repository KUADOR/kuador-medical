import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { expectNoAxeViolations } from '../../../testing/axe';
import { el, render, settle } from '../../../testing/design-system';
import { KdNav, type KdNavItem } from './nav';
import { KdShell } from './shell';

@Component({ template: '<p>Página</p>' })
class Blank {}

@Component({
  imports: [KdShell, KdNav],
  template: `
    <kd-shell brand="Medical Kuador">
      <kd-nav kdShellNav label="Navegación principal" [items]="items" />
      <span kdShellAccount>Cuenta de prueba</span>
      <h1>Agenda</h1>
    </kd-shell>
  `,
})
class Host {
  readonly items: KdNavItem[] = [
    { label: 'Agenda', link: '/agenda', icon: 'calendar' },
    { label: 'Pacientes', link: '/patients', icon: 'users' },
  ];
}

async function renderShell() {
  const fixture = await render(Host);
  TestBed.inject(Router).resetConfig([
    { path: 'agenda', component: Blank },
    { path: 'patients', component: Blank },
  ]);
  return fixture;
}

const menuButton = (root: HTMLElement) =>
  root.querySelector<HTMLButtonElement>('.kd-shell__menu-button')!;
const navPanel = (root: HTMLElement) => root.querySelector<HTMLElement>('.kd-shell__nav')!;

describe('KdShell', () => {
  it('has a skip link, a banner, the navigation and the main landmark', async () => {
    const root = el(await renderShell());

    const skip = root.querySelector<HTMLAnchorElement>('.kd-shell__skip')!;
    expect(skip.textContent?.trim()).toBe('Saltar al contenido');
    expect(root.querySelector(skip.getAttribute('href')!)?.tagName).toBe('MAIN');
    expect(root.querySelector('header')).not.toBeNull();
    expect(root.querySelector('nav')?.getAttribute('aria-label')).toBe('Navegación principal');
    expect(root.querySelector('main h1')?.textContent).toBe('Agenda');
    expect(root.querySelector('[kdShellAccount]')?.closest('header')).not.toBeNull();
  });

  it('opens and closes the navigation with the menu button (small screens)', async () => {
    const fixture = await renderShell();
    const root = el(fixture);
    const button = menuButton(root);

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(button.getAttribute('aria-controls')).toBe(navPanel(root).id);
    expect(button.getAttribute('aria-label')).toBe('Abrir menú');

    button.click();
    await settle(fixture);
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe('Cerrar menú');
    expect(navPanel(root).classList).toContain('is-open');

    button.click();
    await settle(fixture);
    expect(navPanel(root).classList).not.toContain('is-open');
  });

  it('closes the navigation with Escape and returns focus to the menu button', async () => {
    const fixture = await renderShell();
    const root = el(fixture);
    menuButton(root).click();
    await settle(fixture);
    root.querySelector<HTMLAnchorElement>('nav a')!.focus();

    root
      .querySelector('kd-shell')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle(fixture);

    expect(navPanel(root).classList).not.toContain('is-open');
    expect(document.activeElement).toBe(menuButton(root));
  });

  it('closes the navigation after following a link', async () => {
    const fixture = await renderShell();
    const root = el(fixture);
    menuButton(root).click();
    await settle(fixture);

    root.querySelectorAll<HTMLAnchorElement>('nav a')[1].click();
    await settle(fixture);

    expect(TestBed.inject(Router).url).toBe('/patients');
    expect(navPanel(root).classList).not.toContain('is-open');
  });

  it('has no accessibility violations, closed and open (axe)', async () => {
    const fixture = await renderShell();
    await expectNoAxeViolations(el(fixture));

    menuButton(el(fixture)).click();
    await settle(fixture);
    await expectNoAxeViolations(el(fixture));
  });
});

describe('KdNav', () => {
  it('marks the current page with aria-current and the active class', async () => {
    const fixture = await renderShell();
    await TestBed.inject(Router).navigateByUrl('/agenda');
    await settle(fixture);
    const links = el(fixture).querySelectorAll<HTMLAnchorElement>('nav a');

    expect(links[0].getAttribute('aria-current')).toBe('page');
    expect(links[0].classList).toContain('is-active');
    expect(links[1].getAttribute('aria-current')).toBeNull();
    expect(links[1].getAttribute('href')).toBe('/patients');
    expect(links[1].querySelector('kd-icon')?.getAttribute('aria-hidden')).toBe('true');
  });
});
