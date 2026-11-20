import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Data table container: projects a native `<table>` (with its own `<caption>` or label) and
 * gives it the design system look. On narrow screens it scrolls sideways; the scroll area is a
 * named, focusable region so keyboard users can scroll it too.
 */
@Component({
  selector: 'kd-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="kd-table__scroll" role="region" tabindex="0" [attr.aria-label]="label()">
    <ng-content />
  </div>`,
  styleUrl: './table.scss',
  host: { class: 'kd-table' },
})
export class KdTable {
  /** Accessible name of the scroll region (e.g. «Personal de la clínica»). */
  readonly label = input.required<string>();
}
