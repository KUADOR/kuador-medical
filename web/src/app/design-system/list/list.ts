import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { KdIcon } from '../icon/icon';
import type { IconName } from '../icon/icons.generated';

/** List of rows (patients, appointments, branches). Give it a `label` when there is no heading. */
@Component({
  selector: 'kd-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  styleUrl: './list.scss',
  host: {
    class: 'kd-list',
    role: 'list',
    '[attr.aria-label]': 'label()',
  },
})
export class KdList {
  readonly label = input<string | null>(null);
}

/**
 * Row of a `kd-list`: optional leading icon, heading, description and meta. With `link` the
 * row is one link (44 px target); end actions (`[kdListItemEnd]`) stay outside the link.
 * `selected` marks it with the soft accent selection (R-10) and `aria-current`.
 */
@Component({
  selector: 'kd-list-item',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NgTemplateOutlet, KdIcon],
  template: `
    <ng-template #content>
      @if (icon(); as name) {
        <span class="kd-list-item__icon"><kd-icon [name]="name" [size]="20" /></span>
      }
      <span class="kd-list-item__text">
        <span class="kd-list-item__heading">{{ heading() }}</span>
        @if (description()) {
          <span class="kd-list-item__description">{{ description() }}</span>
        }
      </span>
      @if (meta()) {
        <span class="kd-list-item__meta">{{ meta() }}</span>
      }
    </ng-template>
    @if (link(); as target) {
      <a
        class="kd-list-item__row"
        [routerLink]="target"
        [attr.aria-current]="selected() ? 'true' : null"
      >
        <ng-container [ngTemplateOutlet]="content" />
      </a>
    } @else {
      <div class="kd-list-item__row">
        <ng-container [ngTemplateOutlet]="content" />
      </div>
    }
    <ng-content select="[kdListItemEnd]" />
  `,
  styleUrl: './list-item.scss',
  host: {
    class: 'kd-list-item',
    role: 'listitem',
    '[class.is-selected]': 'selected()',
  },
})
export class KdListItem {
  readonly heading = input.required<string>();
  readonly description = input<string | null>(null);
  readonly meta = input<string | null>(null);
  readonly icon = input<IconName | null>(null);
  readonly link = input<string | readonly unknown[] | null>(null);
  readonly selected = input(false, { transform: booleanAttribute });
}
