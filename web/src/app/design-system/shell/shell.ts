import { ChangeDetectionStrategy, Component, ElementRef, input, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { KdIconButton } from '../button/button';

/**
 * Responsive application shell: skip link, top bar (brand, menu button, account area), main
 * navigation and the main landmark.
 * - Wide screens (≥ 64rem): the navigation is a fixed side column; the menu button is hidden.
 * - Small screens: the navigation opens below the bar with the menu button and closes with
 *   Escape, the button again or after following a link.
 *
 * Slots: `[kdShellNav]` (usually a `kd-nav`), `[kdShellAccount]` and the page content.
 */
@Component({
  selector: 'kd-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, KdIconButton],
  template: `
    <a class="kd-shell__skip" href="#kd-main" (click)="focusMain($event)">Saltar al contenido</a>
    <header class="kd-shell__bar">
      <button
        #menuButton
        kdIconButton
        type="button"
        class="kd-shell__menu-button"
        [icon]="navOpen() ? 'x' : 'menu'"
        [label]="navOpen() ? 'Cerrar menú' : 'Abrir menú'"
        aria-controls="kd-shell-nav"
        [attr.aria-expanded]="navOpen()"
        (click)="navOpen.set(!navOpen())"
      ></button>
      <a class="kd-shell__brand" [routerLink]="brandLink()">{{ brand() }}</a>
      <div class="kd-shell__account"><ng-content select="[kdShellAccount]" /></div>
    </header>
    <div class="kd-shell__body">
      <div
        id="kd-shell-nav"
        class="kd-shell__nav"
        [class.is-open]="navOpen()"
        (click)="onNavClick($event)"
      >
        <ng-content select="[kdShellNav]" />
      </div>
      <main #main id="kd-main" class="kd-shell__main" tabindex="-1">
        <ng-content />
      </main>
    </div>
  `,
  styleUrl: './shell.scss',
  host: {
    class: 'kd-shell',
    '(keydown.escape)': 'closeNav()',
  },
})
export class KdShell {
  readonly brand = input.required<string>();
  readonly brandLink = input<string>('/');

  protected readonly navOpen = signal(false);
  private readonly menuButton = viewChild.required('menuButton', {
    read: ElementRef<HTMLButtonElement>,
  });
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  protected closeNav(): void {
    if (this.navOpen()) {
      this.navOpen.set(false);
      this.menuButton().nativeElement.focus();
    }
  }

  /** Following a link of the navigation closes it on small screens. */
  protected onNavClick(event: Event): void {
    if (event.target instanceof Element && event.target.closest('a[href]')) {
      this.navOpen.set(false);
    }
  }

  protected focusMain(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }
}
