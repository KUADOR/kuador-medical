import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { KdIcon } from '../icon/icon';
import type { IconName } from '../icon/icons.generated';

export interface KdNavItem {
  label: string;
  link: string;
  icon?: IconName;
}

/**
 * Main navigation: a labelled list of router links; the current page gets `aria-current="page"`
 * and the accent indicator. The items come from the page (e.g. the menu already filtered by
 * permission and plan module): this component never decides what a person may see.
 */
@Component({
  selector: 'kd-nav',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, KdIcon],
  template: `
    <nav class="kd-nav" [attr.aria-label]="label()">
      <ul class="kd-nav__list">
        @for (item of items(); track item.link) {
          <li>
            <a
              class="kd-nav__link"
              [routerLink]="item.link"
              routerLinkActive="is-active"
              ariaCurrentWhenActive="page"
              (click)="navigate.emit()"
            >
              @if (item.icon) {
                <kd-icon [name]="item.icon" [size]="20" />
              }
              <span>{{ item.label }}</span>
            </a>
          </li>
        }
      </ul>
    </nav>
  `,
  styleUrl: './nav.scss',
})
export class KdNav {
  readonly items = input.required<readonly KdNavItem[]>();
  readonly label = input.required<string>();
  /** Fired when a link is followed (the shell closes its menu on small screens). */
  readonly navigate = output<void>();
}
