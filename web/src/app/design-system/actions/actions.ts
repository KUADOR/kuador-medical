import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Row of buttons or links that wraps on narrow screens (e.g. «Guardar» and «Cancelar»). */
@Component({
  selector: 'kd-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  styleUrl: './actions.scss',
  host: {
    class: 'kd-actions',
    '[class.kd-actions--end]': 'align() === "end"',
  },
})
export class KdActions {
  readonly align = input<'start' | 'end'>('start');
}
