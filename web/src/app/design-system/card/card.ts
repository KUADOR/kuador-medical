import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let nextId = 0;

/**
 * Card: a raised panel with a heading (named region), optional description, body and an
 * actions footer (`[kdCardActions]`). No shadow: cards are fixed content (R-03, R-04).
 */
@Component({
  selector: 'kd-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kd-card__header">
      @switch (headingLevel()) {
        @case (3) {
          <h3 class="kd-card__heading" [id]="headingId">{{ heading() }}</h3>
        }
        @case (4) {
          <h4 class="kd-card__heading" [id]="headingId">{{ heading() }}</h4>
        }
        @default {
          <h2 class="kd-card__heading" [id]="headingId">{{ heading() }}</h2>
        }
      }
      @if (description()) {
        <p class="kd-card__description">{{ description() }}</p>
      }
    </div>
    <div class="kd-card__body"><ng-content /></div>
    <div class="kd-card__actions"><ng-content select="[kdCardActions]" /></div>
  `,
  styleUrl: './card.scss',
  host: {
    class: 'kd-card',
    role: 'region',
    '[attr.aria-labelledby]': 'headingId',
  },
})
export class KdCard {
  readonly heading = input.required<string>();
  readonly headingLevel = input<2 | 3 | 4>(2);
  readonly description = input<string | null>(null);

  protected readonly headingId = `kd-card-${++nextId}`;
}
