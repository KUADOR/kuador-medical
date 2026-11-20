import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Content is loading: a spinner and a short text, announced politely. With reduced motion the
 * spinner stops (src/styles/base.css) and the text still says it.
 */
@Component({
  selector: 'kd-loading-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="kd-loading__spinner" aria-hidden="true"></span>
    <span class="kd-loading__label">{{ label() }}</span>
  `,
  styleUrl: './loading-state.scss',
  host: { class: 'kd-loading', role: 'status', 'aria-live': 'polite' },
})
export class KdLoadingState {
  readonly label = input('Cargando…');
}
