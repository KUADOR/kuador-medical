import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { KdButton } from '../button/button';
import { KdIcon } from '../icon/icon';

/**
 * Something could not be loaded: what happened, what to do (tone of DESIGN.md) and a retry
 * button. Announced as an alert when it appears.
 */
@Component({
  selector: 'kd-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon, KdButton],
  template: `
    <span class="kd-state__icon kd-state__icon--error"><kd-icon name="circle-alert" /></span>
    @if (headingLevel() === 3) {
      <h3 class="kd-state__heading">{{ heading() }}</h3>
    } @else {
      <h2 class="kd-state__heading">{{ heading() }}</h2>
    }
    <p class="kd-state__description">{{ description() }}</p>
    @if (retryable()) {
      <div class="kd-state__actions">
        <button kdButton type="button" icon="refresh-cw" (click)="retry.emit()">
          {{ retryLabel() }}
        </button>
      </div>
    }
  `,
  styleUrl: './state.scss',
  host: { class: 'kd-state kd-error-state', role: 'alert' },
})
export class KdErrorState {
  readonly heading = input('No pudimos cargar la información');
  readonly description = input('Revisa tu conexión e inténtalo de nuevo.');
  readonly retryLabel = input('Reintentar');
  readonly retryable = input(true, { transform: booleanAttribute });
  readonly headingLevel = input<2 | 3>(2);
  readonly retry = output<void>();
}
