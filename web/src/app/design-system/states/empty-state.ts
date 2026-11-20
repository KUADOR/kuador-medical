import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { KdIcon } from '../icon/icon';
import type { IconName } from '../icon/icons.generated';

/** Nothing to show yet: icon, heading, one sentence and, optionally, the action that fixes it. */
@Component({
  selector: 'kd-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `
    <span class="kd-state__icon"><kd-icon [name]="icon()" /></span>
    @if (headingLevel() === 3) {
      <h3 class="kd-state__heading">{{ heading() }}</h3>
    } @else {
      <h2 class="kd-state__heading">{{ heading() }}</h2>
    }
    @if (description()) {
      <p class="kd-state__description">{{ description() }}</p>
    }
    <div class="kd-state__actions"><ng-content select="[kdEmptyStateActions]" /></div>
  `,
  styleUrl: './state.scss',
  host: { class: 'kd-state kd-empty-state' },
})
export class KdEmptyState {
  readonly heading = input.required<string>();
  readonly description = input<string | null>(null);
  readonly icon = input<IconName>('clipboard-list');
  readonly headingLevel = input<2 | 3>(2);
}
