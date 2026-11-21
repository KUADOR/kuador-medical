import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { KdButton, KdPage } from '../../design-system';

/**
 * Simple informative page driven by route data (title, message).
 * Used for forbidden and not found.
 */
@Component({
  selector: 'mk-message-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, KdButton, KdPage],
  template: `
    <kd-page [heading]="heading()" [description]="message()" headingId="message-title">
      <p><a kdButton icon="house" routerLink="/">Ir al estado del sistema</a></p>
    </kd-page>
  `,
})
export class MessagePage {
  /** Bound from route data through withComponentInputBinding(). */
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
}
