import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { KdIcon } from '../icon/icon';
import type { IconName } from '../icon/icons.generated';

/** Tone of a badge. The text always says the state; the tone only supports it (R-08). */
export type KdBadgeTone = 'neutral' | 'info' | 'ok' | 'warning' | 'error';

/** Small label for a state or a category (e.g. «Activo», «Rol clínico»). Radius `sm` (R-11). */
@Component({
  selector: 'kd-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `@if (icon(); as name) {
      <kd-icon [name]="name" [size]="20" />
    }
    <ng-content />`,
  styleUrl: './badge.scss',
  host: {
    '[class]': '"kd-badge kd-badge--" + tone()',
  },
})
export class KdBadge {
  readonly tone = input<KdBadgeTone>('neutral');
  /** Decorative icon before the text. */
  readonly icon = input<IconName | null>(null);
}
