import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { KdIcon } from '../icon/icon';
import type { IconName } from '../icon/icons.generated';

/**
 * Button styles of the design system.
 * - `primary`: the one filled accent action of a view (R-02). There is no other filled variant
 *   in the accent, and it is never the default.
 * - `secondary` (default): outlined.
 * - `tertiary`: text only, for low-emphasis actions (Cancel, Back).
 * - `danger`: destructive confirmation, filled with the error color.
 */
export type KdButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger';

/** Native `<button>` or `<a>` with the design system look. Keeps native keyboard and disabled. */
@Component({
  selector: 'button[kdButton], a[kdButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `@if (icon(); as name) {
      <kd-icon [name]="name" [size]="20" />
    }
    <ng-content />`,
  styleUrl: './button.scss',
  host: {
    class: 'kd-button',
    '[class.kd-button--primary]': 'variant() === "primary"',
    '[class.kd-button--secondary]': 'variant() === "secondary"',
    '[class.kd-button--tertiary]': 'variant() === "tertiary"',
    '[class.kd-button--danger]': 'variant() === "danger"',
  },
})
export class KdButton {
  readonly variant = input<KdButtonVariant>('secondary');
  /** Decorative icon before the text. */
  readonly icon = input<IconName | null>(null);
}

/** Icon-only button: 44 × 44 px target (R-05) and a required accessible name. */
@Component({
  selector: 'button[kdIconButton], a[kdIconButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `<kd-icon [name]="icon()" />`,
  styleUrl: './icon-button.scss',
  host: {
    class: 'kd-icon-button',
    '[attr.aria-label]': 'label()',
  },
})
export class KdIconButton {
  readonly icon = input.required<IconName>();
  readonly label = input.required<string>();
}
