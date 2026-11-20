import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
  output,
} from '@angular/core';
import { KdIconButton } from '../button/button';
import { KdIcon } from '../icon/icon';
import type { IconName } from '../icon/icons.generated';

export type KdAlertTone = 'info' | 'ok' | 'warning' | 'error';

const TONE_ICONS: Record<KdAlertTone, IconName> = {
  info: 'info',
  ok: 'circle-check',
  warning: 'triangle-alert',
  error: 'circle-alert',
};

/**
 * Inline notice. The tone is said with an icon and the text, not only with color (R-08).
 * Errors are announced at once (`role="alert"`); the other tones politely (`role="status"`).
 */
@Component({
  selector: 'kd-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdIcon, KdIconButton],
  template: `
    <kd-icon class="kd-alert__icon" [name]="iconName()" />
    <div class="kd-alert__content">
      @if (heading()) {
        <p class="kd-alert__heading">{{ heading() }}</p>
      }
      <ng-content />
    </div>
    @if (dismissible()) {
      <button
        kdIconButton
        type="button"
        icon="x"
        [label]="dismissLabel()"
        (click)="dismissed.emit()"
      ></button>
    }
  `,
  styleUrl: './alert.scss',
  host: {
    '[class]': '"kd-alert kd-alert--" + tone()',
    '[attr.role]': 'tone() === "error" ? "alert" : "status"',
  },
})
export class KdAlert {
  readonly tone = input<KdAlertTone>('info');
  readonly heading = input<string | null>(null);
  readonly dismissible = input(false, { transform: booleanAttribute });
  readonly dismissLabel = input('Cerrar aviso');
  /** The page removes the alert when this fires. */
  readonly dismissed = output<void>();

  protected readonly iconName = computed(() => TONE_ICONS[this.tone()]);
}
