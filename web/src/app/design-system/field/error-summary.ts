import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { KdIcon } from '../icon/icon';

export interface KdFormError {
  message: string;
  /** Id of the control to focus from the summary; without it the item is plain text. */
  fieldId?: string;
}

/**
 * Summary of the errors of a form, announced as an alert. Each error with a `fieldId` links to
 * its control and moves the focus there. Renders nothing while there are no errors.
 */
@Component({
  selector: 'kd-error-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon],
  template: `
    @if (errors().length > 0) {
      <div class="kd-error-summary" role="alert" tabindex="-1">
        <h2 class="kd-error-summary__heading">
          <kd-icon name="circle-alert" />
          {{ heading() }}
        </h2>
        <ul class="kd-error-summary__list">
          @for (error of errors(); track $index) {
            <li>
              @if (error.fieldId; as id) {
                <a [href]="'#' + id" (click)="focusField($event, id)">{{ error.message }}</a>
              } @else {
                {{ error.message }}
              }
            </li>
          }
        </ul>
      </div>
    }
  `,
  styleUrl: './error-summary.scss',
})
export class KdErrorSummary {
  readonly errors = input<readonly KdFormError[]>([]);
  readonly heading = input('Revisa los datos marcados');

  protected focusField(event: Event, id: string): void {
    const field = document.getElementById(id);
    if (field) {
      event.preventDefault();
      field.focus();
      field.scrollIntoView?.({ block: 'center' });
    }
  }
}
