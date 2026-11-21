import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Term / value pairs of a record on a native `<dl>` (e.g. the data of a staff member). Two
 * columns from 30rem, one below; long values wrap.
 */
@Component({
  selector: 'dl[kdDetails]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
  styleUrl: './details.scss',
  host: { class: 'kd-details' },
})
export class KdDetails {}
