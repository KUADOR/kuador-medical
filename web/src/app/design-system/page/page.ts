import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Width of a page:
 * - `default`: forms and record views.
 * - `wide`: tables and long lists.
 * - `narrow`: one short task (public account pages), shown as a centered raised panel, like
 *   the login (R-03, no shadow).
 */
export type KdPageWidth = 'default' | 'wide' | 'narrow';

let nextId = 0;

/**
 * Layout of a screen: the region named after its `h1`, an optional line under the heading,
 * header actions (`[kdPageActions]`), an optional slot above the heading (`[kdPageBefore]`, e.g.
 * a back link) and the content, spaced with the tokens.
 */
@Component({
  selector: 'kd-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-content select="[kdPageBefore]" />
    <header class="kd-page__header">
      <div class="kd-page__titles">
        <h1 class="kd-page__heading" [id]="headingId()" tabindex="-1">{{ heading() }}</h1>
        @if (description()) {
          <p class="kd-page__description">{{ description() }}</p>
        }
        <ng-content select="[kdPageDescription]" />
      </div>
      <div class="kd-page__actions"><ng-content select="[kdPageActions]" /></div>
    </header>
    <ng-content />
  `,
  styleUrl: './page.scss',
  host: {
    class: 'kd-page',
    role: 'region',
    '[class.kd-page--wide]': 'width() === "wide"',
    '[class.kd-page--narrow]': 'width() === "narrow"',
    '[attr.aria-labelledby]': 'headingId()',
  },
})
export class KdPage {
  readonly heading = input.required<string>();
  readonly description = input<string | null>(null);
  readonly width = input<KdPageWidth>('default');
  /** Id of the `h1`; pages that already reference it (e.g. `aria-labelledby`) can set it. */
  readonly headingId = input(`kd-page-${++nextId}`);
}
